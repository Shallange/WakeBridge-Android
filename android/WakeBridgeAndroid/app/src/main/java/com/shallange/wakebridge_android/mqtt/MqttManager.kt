package com.shallange.wakebridge_android.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient

class MqttManager(
    private val host: String,
    private val username: String,
    private val password: String
) {

    private val client: Mqtt3AsyncClient =
        MqttClient.builder()
            .useMqttVersion3()
            .identifier("wakebridge-android")
            .serverHost(host)
            .serverPort(8883)
            .sslWithDefaultConfig()
            .buildAsync()

    fun connect() {
        client.connectWith()
            .simpleAuth()
            .username(username)
            .password(password.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    println("MQTT connected")
                } else {
                    println("MQTT connection failed: ${throwable.message}")
                }
            }
    }
    fun publishWakeCommand(onResult: (Boolean) -> Unit) {
        client.publishWith()
            .topic("wakebridge/desktop/command")
            .payload("WAKE".toByteArray())
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    println("WAKE command published")
                    onResult(true)
                } else {
                    println("Failed to publish WAKE: ${throwable.message}")
                    onResult(false)
                }
            }
    }

    fun subscribeToStatus(onStatusReceived: (String) -> Unit) {
        client.subscribeWith()
            .topicFilter("wakebridge/desktop/status")
            .callback { publish ->
                val message = publish.payloadAsBytes.toString(Charsets.UTF_8)

                println("Status received: $message")

                onStatusReceived(message)
            }
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    println("Subscribed to status topic")
                } else {
                    println("Failed to subscribe to status: ${throwable.message}")
                }
            }
    }
    fun subscribeToEsp32Status(onStatusReceived: (String) -> Unit) {
        client.subscribeWith()
            .topicFilter("wakebridge/esp32/status")
            .callback { publish ->
                val message = publish.payloadAsBytes.toString(Charsets.UTF_8)

                println("ESP32 status received: $message")

                onStatusReceived(message)
            }
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    println("Subscribed to ESP32 status topic")
                } else {
                    println("Failed to subscribe to ESP32 status: ${throwable.message}")
                }
            }
    }

    fun publishCheckCommand(onResult: (Boolean) -> Unit) {
        client.publishWith()
            .topic("wakebridge/desktop/check")
            .payload("CHECK".toByteArray())
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    println("CHECK command published")
                    onResult(true)
                } else {
                    println("Failed to publish CHECK: ${throwable.message}")
                    onResult(false)
                }
            }
    }

    fun subscribeToPower(onPowerReceived: (String) -> Unit) {
        client.subscribeWith()
            .topicFilter("wakebridge/desktop/power")
            .callback { publish ->
                val message = publish.payloadAsBytes.toString(Charsets.UTF_8)

                println("PC power received: $message")

                onPowerReceived(message)
            }
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    println("Subscribed to PC power topic")
                } else {
                    println("Failed to subscribe to PC power: ${throwable.message}")
                }
            }
    }
}

data class PcPowerStatus(
    val online: Boolean,
    val checkedAtUnix: Long
)

fun parsePcPower(payload: String): PcPowerStatus? {
    val parts = payload.trim().split('|')
    if (parts.size != 2) {
        return null
    }

    val online = when (parts[0]) {
        "online" -> true
        "offline" -> false
        else -> return null
    }

    val checkedAtUnix = parts[1].toLongOrNull() ?: return null
    if (checkedAtUnix < 0) {
        return null
    }

    return PcPowerStatus(online, checkedAtUnix)
}
