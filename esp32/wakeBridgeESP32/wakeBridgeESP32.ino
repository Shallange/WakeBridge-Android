#include <WiFi.h>
#include <WiFiUdp.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include "ping/ping_sock.h"
#include <time.h>
#include "secrets.h"
#include "mqtt_ca.h"

#define WOL_BROADCAST_IP "192.168.1.255"
#define WOL_PORT 9

// Reachability timing. The PC address is PC_IP_1..PC_IP_4 in secrets.h.
// Pings run only after CHECK, or for a short time after WAKE.
#define PC_PING_TIMEOUT_MS 500
#define PC_WAKE_CHECK_INTERVAL_MS 3000
#define PC_WAKE_WATCH_MS 60000

#define MQTT_CLIENT_ID "wakebridge-esp32"
#define MQTT_COMMAND_TOPIC "wakebridge/desktop/command"
#define MQTT_STATUS_TOPIC "wakebridge/desktop/status"
#define MQTT_CHECK_TOPIC "wakebridge/desktop/check"
#define MQTT_POWER_TOPIC "wakebridge/desktop/power"
#define MQTT_DEVICE_STATUS_TOPIC "wakebridge/esp32/status"

// A clock still at the epoch fails TLS certificate date checks.
static const time_t MIN_VALID_TIME = 1704067200;  // 2024-01-01 00:00:00 UTC
static const unsigned long NTP_WAIT_MS = 15000;

WiFiUDP udp;
// Secure TCP client used for MQTT over TLS.
WiFiClientSecure secureClient;

// MQTT client that uses the secure TLS connection above.
PubSubClient mqttClient(secureClient);

// ICMP callbacks run on the ping task. loop() runs on the Arduino task.
// Both cores only share these through atomic loads and stores.
uint8_t pcPingReplySeen = 0;
uint8_t pcPingResult = 0;
bool pcCheckInProgress = false;
esp_ping_handle_t pcPingHandle = nullptr;
bool pingRequested = false;
bool wakeWatchActive = false;
unsigned long wakeWatchStartedMs = 0;
unsigned long lastWakeCheckMs = 0;
char pendingPowerPayload[32] = "";
bool powerPublishPending = false;

bool clockIsValid() {
  return time(nullptr) >= MIN_VALID_TIME;
}

// SNTP is started with configTime() and finishes in the background.
// TLS is attempted only after this returns true.
bool waitForClock() {
  Serial.print("Waiting for NTP");
  unsigned long startedMs = millis();
  while (!clockIsValid() && millis() - startedMs < NTP_WAIT_MS) {
    delay(250);
    Serial.print(".");
  }
  Serial.println();

  if (!clockIsValid()) {
    Serial.println("NTP time not available; TLS will retry");
    return false;
  }

  time_t now = time(nullptr);
  struct tm timeinfo;
  gmtime_r(&now, &timeinfo);
  char timestamp[32];
  strftime(timestamp, sizeof(timestamp), "%Y-%m-%d %H:%M:%S UTC", &timeinfo);
  Serial.print("NTP time is valid: ");
  Serial.println(timestamp);
  return true;
}

void setup() {
  Serial.begin(115200);

  connectWiFi();
  configTime(0, 0, "pool.ntp.org");
  secureClient.setCACert(MQTT_ROOT_CA);
  if (waitForClock()) {
    connectMqtt();
  }
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi connection lost, reconnecting...");
    WiFi.reconnect();
    delay(1000);
    return;
  }

  if (!mqttClient.connected()) {
    if (!clockIsValid()) {
      Serial.println("NTP time not available; skipping TLS");
      delay(2000);
      return;
    }
    connectMqtt();
  }

  // Keeps the MQTT connection alive and processes incoming messages.
  mqttClient.loop();
  updatePcReachability();
}

void connectWiFi() {
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");
  
  while (WiFi.status() != WL_CONNECTED){
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.println("WiFi connected");

  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
}

void sendWakePacket() {
  // A Wake-on-LAN magic packet contains:
  // 6 bytes of 0xFF followed by the target MAC address repeated 16 times.
  uint8_t packet[102];

  // Fill the first 6 bytes with 0xFF
  for (int i = 0; i < 6; i++){
    packet[i] = 0xFF;
  }

  // Repeat the target PC's MAC address 16 times.
  for (int i = 1; i <= 16; i++) {
    for (int j = 0; j < 6; j++) {
      packet[i * 6 + j] = PC_MAC[j];
    }
  }
  //Broadcast the magic packet on the local network
  udp.beginPacket(WOL_BROADCAST_IP, WOL_PORT);
  udp.write(packet, sizeof(packet));
  udp.endPacket();

  Serial.println("Wake-on-LAN packet sent");
}

void connectMqtt() {
  // Configure the MQTT broker and the function that handles incoming messages.
  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);

  Serial.print("Connecting to MQTT");
  
  // Keep trying until a connection to the broker succeeds.
  while (!mqttClient.connected()) {
    if (mqttClient.connect(
      MQTT_CLIENT_ID,
      MQTT_USERNAME,
      MQTT_PASSWORD,
      
      // MQTT Last Will:
      // If the ESP32 disconnects unexpectedly, the broker will
      // automatically publish "offline" on this topic.
      MQTT_DEVICE_STATUS_TOPIC,
      0,        // QoS 0 is enough for simple online/offline presence.
      true,     // Retain the offline status for future subscribers.
      "offline" // Message published by the broker if the ESP32 disappears.
    )) {
      Serial.println();
      Serial.println("MQTT connected");
      mqttClient.subscribe(MQTT_COMMAND_TOPIC);
      Serial.println("Subscribed to command topic");
      mqttClient.subscribe(MQTT_CHECK_TOPIC);
      Serial.println("Subscribed to check topic");
      mqttClient.publish(
        MQTT_DEVICE_STATUS_TOPIC,
        "online",
        true
      );
      Serial.println("Published ESP32 status: online");

    } else {
      Serial.print(".");
      Serial.print(" state=");
      Serial.println(mqttClient.state());
      char tlsError[96];
      int tlsCode = secureClient.lastError(tlsError, sizeof(tlsError));
      if (tlsCode != 0) {
        Serial.print("TLS error ");
        Serial.print(tlsCode);
        Serial.print(": ");
        Serial.println(tlsError);
      }
      delay(2000);
    }
  }
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  // Convert the incoming MQTT payload from raw bytes into a String.
  String message;

  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.print("Message received on topic: ");
  Serial.println(topic);

  Serial.print("Payload: ");
  Serial.println(message);

  // Trigger Wake-on-LAN when the expected MQTT command is received.
  if (
    strcmp(topic, MQTT_COMMAND_TOPIC) == 0 &&
    message == "WAKE"
  ) {
    sendWakePacket();
    // Confirm that the ESP32 processed the wake command.
    mqttClient.publish(MQTT_STATUS_TOPIC, "wake_sent");
    // Power detection is separate from the wake acknowledgement.
    beginWakeWatch();
  }

  // One reachability check. Ignored while a ping or wake watch is already running.
  if (
    strcmp(topic, MQTT_CHECK_TOPIC) == 0 &&
    message == "CHECK" &&
    !pcCheckInProgress &&
    !wakeWatchActive
  ) {
    pingRequested = true;
  }
}

void onPcPingSuccess(esp_ping_handle_t, void *) {
  __atomic_store_n(&pcPingReplySeen, 1, __ATOMIC_RELEASE);
}

void onPcPingEnd(esp_ping_handle_t, void *) {
  // 1 = online, 2 = offline. One store so loop() can read a finished result.
  uint8_t seen = __atomic_load_n(&pcPingReplySeen, __ATOMIC_ACQUIRE);
  __atomic_store_n(&pcPingResult, seen ? 1 : 2, __ATOMIC_RELEASE);
}

void queuePcPowerPublish(bool online) {
  time_t now = time(nullptr);
  long checkedAt = now > 1000000000 ? (long)now : 0;

  snprintf(
    pendingPowerPayload,
    sizeof(pendingPowerPayload),
    "%s|%ld",
    online ? "online" : "offline",
    checkedAt
  );
  powerPublishPending = true;
  Serial.print("PC power: ");
  Serial.println(pendingPowerPayload);

  if (!mqttClient.connected()) {
    return;
  }

  if (mqttClient.publish(MQTT_POWER_TOPIC, pendingPowerPayload, true)) {
    powerPublishPending = false;
  }
}

void retryPcPowerPublish() {
  if (!powerPublishPending || !mqttClient.connected()) {
    return;
  }

  if (mqttClient.publish(MQTT_POWER_TOPIC, pendingPowerPayload, true)) {
    powerPublishPending = false;
  }
}

void beginWakeWatch() {
  unsigned long now = millis();
  wakeWatchActive = true;
  wakeWatchStartedMs = now;
  // The next updatePcReachability() starts a ping immediately.
  lastWakeCheckMs = now - PC_WAKE_CHECK_INTERVAL_MS;
  if (!pcCheckInProgress) {
    pingRequested = true;
  }
}

void startPcPing() {
  esp_ping_config_t config = ESP_PING_DEFAULT_CONFIG();
  IP_ADDR4(&config.target_addr, PC_IP_1, PC_IP_2, PC_IP_3, PC_IP_4);
  config.count = 1;
  config.timeout_ms = PC_PING_TIMEOUT_MS;

  esp_ping_callbacks_t callbacks = {};
  callbacks.on_ping_success = onPcPingSuccess;
  callbacks.on_ping_end = onPcPingEnd;

  __atomic_store_n(&pcPingReplySeen, 0, __ATOMIC_RELEASE);
  __atomic_store_n(&pcPingResult, 0, __ATOMIC_RELEASE);

  esp_err_t sessionErr = esp_ping_new_session(&config, &callbacks, &pcPingHandle);
  if (sessionErr != ESP_OK) {
    Serial.print("PC ping session failed: ");
    Serial.println(sessionErr);
    return;
  }

  esp_err_t startErr = esp_ping_start(pcPingHandle);
  if (startErr != ESP_OK) {
    esp_ping_delete_session(pcPingHandle);
    pcPingHandle = nullptr;
    Serial.print("PC ping start failed: ");
    Serial.println(startErr);
    return;
  }

  pcCheckInProgress = true;
}

void updatePcReachability() {
  retryPcPowerPublish();

  uint8_t result = __atomic_load_n(&pcPingResult, __ATOMIC_ACQUIRE);
  if (result != 0) {
    __atomic_store_n(&pcPingResult, 0, __ATOMIC_RELAXED);
    bool online = result == 1;
    Serial.println(online ? "PC ONLINE" : "PC OFFLINE");

    if (pcPingHandle != nullptr) {
      esp_ping_delete_session(pcPingHandle);
      pcPingHandle = nullptr;
    }
    pcCheckInProgress = false;
    queuePcPowerPublish(online);

    if (wakeWatchActive && (online || (millis() - wakeWatchStartedMs >= PC_WAKE_WATCH_MS))) {
      wakeWatchActive = false;
    }
  }

  if (pcCheckInProgress) {
    return;
  }

  if (wakeWatchActive && (millis() - wakeWatchStartedMs >= PC_WAKE_WATCH_MS)) {
    wakeWatchActive = false;
  }

  unsigned long now = millis();
  if (wakeWatchActive && (now - lastWakeCheckMs >= PC_WAKE_CHECK_INTERVAL_MS)) {
    pingRequested = true;
  }

  if (!pingRequested) {
    return;
  }

  pingRequested = false;
  lastWakeCheckMs = now;
  startPcPing();
}
