package com.shallange.wakebridge_android.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MqttManagerReconnectTest {

    @Test
    fun usesHiveMqBackoffAndKeepsTheGivenClientId() {
        val manager = MqttManager(
            host = "example.invalid",
            username = "user",
            password = "password",
            clientId = "wakebridge-android-test-device"
        )

        val reconnect = manager.automaticReconnect()
        assertNotNull(reconnect)
        assertEquals(1, reconnect!!.getInitialDelay(TimeUnit.SECONDS))
        assertEquals(120, reconnect.getMaxDelay(TimeUnit.SECONDS))
        assertEquals("wakebridge-android-test-device", manager.configuredClientId())
        assertTrue(manager.reconnectHasConfiguredAuth())
    }
}
