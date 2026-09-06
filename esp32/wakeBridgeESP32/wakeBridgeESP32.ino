#include <WiFi.h>
#include <WiFiUdp.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include "secrets.h"

#define WOL_BROADCAST_IP "192.168.1.255"
#define WOL_PORT 9

#define MQTT_CLIENT_ID "wakebridge-esp32"
#define MQTT_COMMAND_TOPIC "wakebridge/desktop/command"
#define MQTT_STATUS_TOPIC "wakebridge/desktop/status"

WiFiUDP udp;
// Secure TCP client used for MQTT over TLS.
WiFiClientSecure secureClient;

// MQTT client that uses the secure TLS connection above.
PubSubClient mqttClient(secureClient);

void setup() {
  Serial.begin(115200);

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

  // Temporary for initial MQTT/TLS connection testing
  // Replace with proper certificate verification later.
  secureClient.setInsecure();
  // Connect to the HiveMQ broker after Wi-Fi is available.
  connectMqtt();
}

void loop() {
  // Keeps the MQTT connection alive and processes incoming messages.
  mqttClient.loop();
  // Temporary local test:
  // send a WoL packet when 'w' is entered in the Serial Monitor.
  if (Serial.available()) {
    char command = Serial.read();

    if(command == 'w') {
      sendWakePacket();
    }
  }
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
      MQTT_PASSWORD
    )) {
      Serial.println();
      Serial.println("MQTT connected");
      mqttClient.subscribe(MQTT_COMMAND_TOPIC);
      Serial.println("Subscribed to command topic");

    } else {
      Serial.print(".");
      Serial.print(" state=");
      Serial.println(mqttClient.state());
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
  }
}