# MQTT broker migration

**Status: planned. Not started.**

WakeBridge currently uses HiveMQ Cloud. No replacement broker has been chosen, configured, or tested. This note is the contract a future broker has to meet, and the checklist for moving without losing wake, presence, or power state.

## Current architecture

The Android app and the ESP32 are both MQTT 3 clients. They connect out to HiveMQ Cloud with a username and password. TLS is on port 8883. That port is fixed in both programs.

| Client | Identifier | TLS trust |
| --- | --- | --- |
| Android | `wakebridge-android` | Platform certificate store, via `sslWithDefaultConfig()` |
| ESP32 | `wakebridge-esp32` | ISRG Root X1 in `mqtt_ca.h`, plus the broker hostname |

The broker's current certificate is issued by Let's Encrypt and chains to ISRG Root X1. The firmware trusts that root, not the short-lived leaf. The ESP32 also waits for SNTP before it starts TLS, because the certificate dates are checked against the device clock.

The PC is not an MQTT client. The ESP32 is the only publisher of power and presence.

## Why a move is being considered

The phone and the ESP32 both depend on one hosted broker. A move is being considered so that dependency is explicit and reversible. The replacement has not been selected. Until one is configured and a wake plus a power check have succeeded on it, HiveMQ Cloud remains the broker.

## What a replacement must provide

- MQTT 3 on TLS port 8883, with username and password authentication. Another port or MQTT 5 would require code changes in both clients.
- A server certificate whose name matches the configured host.
- A chain the Android system trust store accepts.
- A chain that ends at ISRG Root X1, or a firmware update that installs the new root before cutover. Disabling ESP32 certificate checks is not an acceptable shortcut.
- Support for retained messages and for a retained last will at QoS 0.
- Stable client sessions for `wakebridge-android` and `wakebridge-esp32`. Those identifiers stay unique to each device.
- Separate usernames for the phone and the ESP32, if the broker can restrict each user to the topics that client publishes and subscribes. WakeBridge does not create those users or store an access-control policy. HiveMQ Cloud is not documented here as already configured that way.

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

The last will is registered on the ESP32 connection: topic `wakebridge/esp32/status`, payload `offline`, QoS 0, retained. After a successful connect, the firmware publishes retained `online` on that same topic. A clean replacement has to keep both behaviors. If the will is not retained, a phone that connects later cannot learn that the bridge is gone.

`WAKE` and `CHECK` must not be retained. A retained command would wake the PC again, or start another ping, every time the ESP32 subscribed.

The power message must stay retained so a newly opened app can show the last reading. The app labels a reading older than three minutes as last known.

## Certificate trust

On the phone, a public certificate that Android already trusts is enough. The app does not pin a single root.

On the ESP32, `secureClient.setCACert()` loads ISRG Root X1. A broker that still chains to that root needs only a new host, username, and password in `secrets.h`. The hostname in the certificate must match `MQTT_HOST`.

A broker that uses another public root, or a private CA, needs that root added to `mqtt_ca.h` and a new firmware flash before the device is pointed at it. The leaf certificate rotates and must not be pinned. An intermediate must not be pinned either.

The ESP32 refuses the handshake until NTP has set the clock. A new network that blocks UDP port 123 to `pool.ntp.org` will stop MQTT even when the certificate is correct.

## Migration checklist

1. Leave the HiveMQ Cloud account and its credentials in place.
2. Create the new broker and a dedicated username. Confirm MQTT 3, TLS, port 8883, retained messages, and last will with a desktop MQTT client before touching the ESP32.
3. Inspect the new server certificate. Confirm the name matches the host you will configure. Confirm the chain ends at a root Android trusts. If that root is not ISRG Root X1, update `mqtt_ca.h` and flash the ESP32 first, while it still points at HiveMQ.
4. Put the new host, username, and password into `esp32/wakeBridgeESP32/secrets.h` and `android/WakeBridgeAndroid/local.properties`. Do not commit either file.
5. Flash the ESP32. Serial should show a valid NTP time, then `MQTT connected`, subscriptions to the command and check topics, and retained `online`.
6. Reinstall or rerun the Android app so the new `local.properties` values are compiled in.
7. Confirm the bridge shows Online. Run Check status and confirm a fresh `online|<unix>` or `offline|<unix>` reading. Run Wake and confirm `wake_sent`, then a later power reading. Confirm the bridge shows Offline after the ESP32 is powered off.
8. Only after those checks pass, stop using the old broker. Retained messages do not copy themselves to the new broker. Expect one fresh check before the phone has a power reading on the new account.

## Rollback

1. Restore the previous host, username, and password in `secrets.h` and `local.properties`.
2. If `mqtt_ca.h` was changed, restore ISRG Root X1 before flashing back, unless the old broker also chains to the replacement root.
3. Flash the ESP32 and rerun the Android app.
4. Confirm presence, one check, and one wake against HiveMQ Cloud again.

The old broker still holds whatever it retained at the time of the move. Rollback reads that state. It does not merge readings from the replacement.
