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
}