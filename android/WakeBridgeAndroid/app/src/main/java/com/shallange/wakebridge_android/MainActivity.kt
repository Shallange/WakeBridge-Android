package com.shallange.wakebridge_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shallange.wakebridge_android.mqtt.MqttManager
import com.shallange.wakebridge_android.ui.theme.WakeBridgeAndroidTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Handler
import android.os.Looper


class MainActivity : ComponentActivity() {
    private val mqttManager = MqttManager(
        host = BuildConfig.MQTT_HOST,
        username = BuildConfig.MQTT_USERNAME,
        password = BuildConfig.MQTT_PASSWORD
    )
    private var esp32Status by mutableStateOf("Connecting...")
    private var lastStatus by mutableStateOf("Waiting")
    private var isWaking by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())

    private val wakeTimeout = Runnable{
        if (isWaking) {
            isWaking = false
            lastStatus = "Timeout"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mqttManager.connect()
        mqttManager.subscribeToStatus { status ->
            runOnUiThread {
                if (status == "wake_sent") {
                    handler.removeCallbacks(wakeTimeout)
                    lastStatus = "Wake sent"
                    isWaking = false
                }
            }
        }
        
        mqttManager.subscribeToEsp32Status { status ->
            runOnUiThread {
                when (status) {
                    "online" -> esp32Status = "Online"
                    "offline" -> esp32Status = "Offline"
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            WakeBridgeAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WakeBridgeScreen(
                        modifier = Modifier.padding(innerPadding),
                        esp32Status = esp32Status,
                        lastStatus = lastStatus,
                        isWaking = isWaking,
                        onWakeClick = {
                            isWaking = true
                            lastStatus = "Sending"

                            handler.removeCallbacks(wakeTimeout)
                            handler.postDelayed(wakeTimeout, 5000)

                            mqttManager.publishWakeCommand { success ->
                                if (!success) {
                                    runOnUiThread {
                                        handler.removeCallbacks(wakeTimeout)
                                        isWaking = false
                                        lastStatus = "Failed"
                                    }
                                }

                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WakeBridgeScreen(
    modifier: Modifier = Modifier,
    esp32Status: String = "Connecting...",
    lastStatus: String = "Waiting",
    isWaking: Boolean = false,
    onWakeClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "WakeBridge",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))
        PcCard(
            pcStatus = "Unknown",
            lastStatus = lastStatus,
            isWaking = isWaking,
            onWakeClick = onWakeClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        BridgeStatusCard(
            esp32Status = esp32Status
        )
    }
}

@Composable
fun PcCard(
    pcStatus: String,
    lastStatus: String,
    isWaking: Boolean,
    onWakeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "PC",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            StatusIndicator(
                status = pcStatus
            )
            Text(
                text = when (lastStatus) {
                    "Sending" -> "Sending wake command..."
                    "Wake sent" -> "Wake command sent"
                    "Failed" -> "Failed to send wake command"
                    "Timeout" -> "No response from bridge"
                    else -> "Ready to wake"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onWakeClick,
                enabled = !isWaking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = when{
                        isWaking -> "Waking..."
                        lastStatus == "Failed" || lastStatus == "Timeout" -> "Try again"
                        else -> "Wake PC"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun BridgeStatusCard(
    esp32Status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bridge",
                fontWeight = FontWeight.Medium
            )

            StatusIndicator(
                status = esp32Status
            )
        }
    }
}

@Composable
fun StatusIndicator(
    status: String
) {
    val statusColor = when (status) {
        "Online" -> Color(0xFF4CAF50)
        "Offline" -> Color(0xFFF44336)
        "Connecting..." -> Color(0xFFFFC107)
        else -> Color.Gray
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = statusColor,
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = status,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WakeBridgeScreenPreview() {
    WakeBridgeAndroidTheme {
        WakeBridgeScreen()
    }
}