# MQTT broker migration

**Status: in use. EMQX Cloud Serverless.**

WakeBridge uses EMQX Cloud Serverless. The previous broker was HiveMQ Cloud. This note is the topic and certificate contract, what changed in the clients, and which checks were run on the hardware.

## Current architecture

The Android app and the ESP32 are both MQTT 3 clients. They connect out to EMQX Cloud Serverless with a username and password. TLS is on port 8883. That port is fixed in both programs.

| Client | Identifier | TLS trust |
| --- | --- | --- |
| Android | `wakebridge-android-` plus the device ANDROID_ID | Platform certificate store, via `sslWithDefaultConfig()` |
| ESP32 | `wakebridge-esp32` | DigiCert Global Root G2 and ISRG Root X1 in `mqtt_ca.h`, plus the broker hostname |

The Android username is shared across installs. The client id is not. Each install reads ANDROID_ID when the screen starts and builds `wakebridge-android-<id>`. A fixed id of `wakebridge-android` would let a second phone take over the first session. Two phones connected at the same time have not been tested.

The ESP32 client id is the fixed string `wakebridge-esp32`. It is separate from the MQTT username.

The PC is not an MQTT client. The ESP32 is the only publisher of power and presence.

The phone and the ESP32 use different broker usernames. WakeBridge does not create those users or store an access-control policy. The broker limits each username to the topics that client publishes and subscribes.

If the phone loses the network while the app process is still running, the MQTT library reconnects and sends the username and password stored on the client. Those credentials have to be set when the client is built. Credentials passed only to the first connect call are not reused, and the broker then rejects the retry. Closing the app stops reconnect. There is no foreground service.

The ESP32 still reconnects from its own connect loop after Wi-Fi or MQTT drops. That loop was not changed for this move.

## What changed

HiveMQ Cloud remains available for rollback. Its retained messages were not copied. EMQX starts without the old power reading, so the phone needs one fresh check before it has a power value on the new broker.

`mqtt_ca.h` holds two public roots. DigiCert Global Root G2 is the trust anchor for EMQX Cloud. ISRG Root X1 stays so a return to HiveMQ Cloud does not need another certificate change. The leaf and the intermediate are not pinned. The EMQX chain uses DigiCert Encryption Everywhere DV TLS CA - G2 between the leaf and DigiCert Global Root G2. Android already trusts DigiCert Global Root G2 through the platform store. The ESP32 loads the concatenated PEM with `setCACert()` after the clock is set. Certificate checks stay on. The root fingerprints are in the comment at the top of `mqtt_ca.h`.

## What the broker has to keep providing

- MQTT 3 on TLS port 8883, with username and password authentication. Another port or MQTT 5 would require code changes in both clients.
- A server certificate whose name matches the configured host.
- A chain the Android system trust store accepts.
- A chain that ends at DigiCert Global Root G2 or ISRG Root X1, or a firmware update that installs the new root before cutover. Disabling ESP32 certificate checks is not an acceptable shortcut.
- Support for retained messages and for a retained last will at QoS 0.
- A distinct MQTT client id per Android install, and `wakebridge-esp32` for this ESP32.
- Separate usernames for the phone and the ESP32, with broker-side limits on the topics each user publishes and subscribes. WakeBridge does not apply those restrictions.

NTP must still be reachable from the ESP32. The firmware will not open TLS until the clock is past 1 January 2024 UTC.

## Topics that must stay the same

Payloads are literal strings. QoS is 0 everywhere.

| Topic | Who publishes | Payload | Retained |
| --- | --- | --- | --- |
| `wakebridge/desktop/command` | Android | `WAKE` | No |
| `wakebridge/desktop/status` | ESP32 | `wake_sent` | No |
| `wakebridge/desktop/check` | Android | `CHECK` | No |
| `wakebridge/desktop/power` | ESP32 | `online\|<unix>` or `offline\|<unix>` | Yes |
| `wakebridge/esp32/status` | ESP32 | `online` | Yes |
| `wakebridge/esp32/status` | Broker last will | `offline` | Yes |

`<unix>` is seconds since the epoch. `0` means the ESP32 did not yet have a synchronized clock when it published. The app does not treat a zero timestamp as a completed check or wake.

`wake_sent` only means the magic packet was sent. The power topic is the online or offline result.

The ESP32 subscribes to `wakebridge/desktop/command` and `wakebridge/desktop/check`. The app subscribes to `wakebridge/desktop/status`, `wakebridge/desktop/power`, and `wakebridge/esp32/status`.

The last will is registered on the ESP32 connection: topic `wakebridge/esp32/status`, payload `offline`, QoS 0, retained. After a successful connect, the firmware publishes retained `online` on that same topic. If the will is not retained, a phone that connects later cannot learn that the bridge is gone.

`WAKE` and `CHECK` must not be retained. A retained command would wake the PC again, or start another ping, every time the ESP32 subscribed.

The power message must stay retained so a newly opened app can show the last reading. The app labels a reading older than three minutes as last known.

A second ESP32 for another PC needs its own topic prefix and its own broker permissions. These topics are one PC. A second device on `wakebridge/desktop/command` could wake this PC.

## Certificate trust

On the phone, a public certificate that Android already trusts is enough. The app does not pin a single root. EMQX Cloud is covered by the platform store through DigiCert Global Root G2.

On the ESP32, `secureClient.setCACert()` loads the roots in `mqtt_ca.h`. The hostname in the certificate must match `MQTT_HOST`.

A broker that uses another public root, or a private CA, needs that root added to `mqtt_ca.h` and a new firmware flash before the device is pointed at it. The leaf certificate rotates and must not be pinned. An intermediate must not be pinned either.

The ESP32 refuses the handshake until NTP has set the clock. A network that blocks UDP port 123 to `pool.ntp.org` will stop MQTT even when the certificate is correct.

## What was checked

On the hardware, after the clients pointed at EMQX Cloud Serverless:

- The Android app connected over TLS.
- The ESP32 connected over TLS after NTP, subscribed to the command and check topics, and published retained `online`.
- Check status produced a fresh power reading.
- Wake published `wake_sent`, and the magic packet brought the PC online. The app did not treat `wake_sent` as the PC being online.
- The retained last will was checked. After the ESP32 session ended, the phone showed the bridge offline.
- With the app still open, turning the phone network off and back on reconnected, delivered messages, and Check worked again.

Not checked:

- Two Android phones connected at the same time.

## Migration checklist

This is the cutover that was used.

1. Leave the HiveMQ Cloud account and its credentials in place.
2. Create the new broker and a dedicated username for each client. Confirm MQTT 3, TLS, port 8883, retained messages, and last will with a desktop MQTT client before touching the ESP32.
3. Inspect the new server certificate. Confirm the name matches the host you will configure. Confirm the chain ends at a root Android trusts. EMQX Cloud chains to DigiCert Global Root G2, so that root was added to `mqtt_ca.h` beside ISRG Root X1 before the ESP32 was pointed at the new host.
4. Put the new host, username, and password into `esp32/wakeBridgeESP32/secrets.h` and `android/WakeBridgeAndroid/local.properties`. Do not commit either file.
5. Flash the ESP32. Serial should show a valid NTP time, then `MQTT connected`, subscriptions to the command and check topics, and retained `online`.
6. Reinstall or rerun the Android app so the new `local.properties` values are compiled in.
7. Confirm the bridge shows Online. Run Check status and confirm a fresh `online|<unix>` or `offline|<unix>` reading. Run Wake and confirm `wake_sent`, then a later power reading. Confirm the bridge shows Offline after the ESP32 is powered off.
8. Only after those checks pass, stop using the old broker. Retained messages do not copy themselves to the new broker. Expect one fresh check before the phone has a power reading on the new account.

## Rollback

1. Restore the previous host, username, and password in `secrets.h` and `local.properties`.
2. Leave `mqtt_ca.h` as it is. It still contains ISRG Root X1, which HiveMQ Cloud chains to, and DigiCert Global Root G2.
3. Flash the ESP32 and rerun the Android app.
4. Confirm presence, one check, and one wake against HiveMQ Cloud again.

The old broker still holds whatever it retained at the time of the move. Rollback reads that state. It does not merge readings from EMQX.
