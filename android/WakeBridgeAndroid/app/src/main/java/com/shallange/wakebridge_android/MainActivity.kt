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
import androidx.compose.material3.OutlinedButton
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
import com.shallange.wakebridge_android.mqtt.PcPowerStatus
import com.shallange.wakebridge_android.mqtt.parsePcPower
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
import java.text.DateFormat
import java.util.Date


class MainActivity : ComponentActivity() {
    private val mqttManager = MqttManager(
        host = BuildConfig.MQTT_HOST,
        username = BuildConfig.MQTT_USERNAME,
        password = BuildConfig.MQTT_PASSWORD
    )
    private var esp32Status by mutableStateOf("Connecting...")
    private var lastStatus by mutableStateOf("Waiting")
    private var isWaking by mutableStateOf(false)
    private var isChecking by mutableStateOf(false)
    private var pcStatus by mutableStateOf("Unknown")
    private var lastCheckedLabel by mutableStateOf("Not checked yet")

    private var awaitingWakeAck = false
    private var lastPowerUnix: Long? = null
    private var checkBaselineUnix = 0L
    private var checkStartedWallSec = 0L
    private var wakeBaselineUnix = 0L
    private var wakeStartedWallSec = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val wakeTimeout = Runnable {
        if (awaitingWakeAck) {
            awaitingWakeAck = false
            isWaking = false
            lastStatus = "Timeout"
            handler.removeCallbacks(wakeWatchTimeout)
        }
    }

    private val wakeWatchTimeout = Runnable {
        if (isWaking) {
            isWaking = false
            lastStatus = "Not online"
        }
    }

    private val checkTimeout = Runnable {
        if (isChecking) {
            isChecking = false
            lastStatus = "Check timeout"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mqttManager.connect()
        mqttManager.subscribeToStatus { status ->
            runOnUiThread {
                if (status == "wake_sent" && awaitingWakeAck) {
                    handler.removeCallbacks(wakeTimeout)
                    awaitingWakeAck = false
                    lastStatus = "Wake sent"
                }
            }
        }

        mqttManager.subscribeToPower { payload ->
            val parsed = parsePcPower(payload)
            if (parsed == null) {
                println("Ignored PC power payload")
                return@subscribeToPower
            }
            runOnUiThread {
                onPowerStatus(parsed)
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
                        pcStatus = pcStatus,
                        lastCheckedLabel = lastCheckedLabel,
                        lastStatus = lastStatus,
                        isWaking = isWaking,
                        isChecking = isChecking,
                        onWakeClick = { requestWake() },
                        onCheckClick = { requestCheck() }
                    )
                }
            }
        }
    }

    private fun requestWake() {
        isWaking = true
        awaitingWakeAck = true
        lastStatus = "Sending"
        wakeBaselineUnix = lastPowerUnix ?: 0L
        wakeStartedWallSec = System.currentTimeMillis() / 1000

        if (isChecking) {
            isChecking = false
            handler.removeCallbacks(checkTimeout)
        }

        handler.removeCallbacks(wakeTimeout)
        handler.removeCallbacks(wakeWatchTimeout)
        handler.postDelayed(wakeTimeout, WAKE_ACK_TIMEOUT_MS)
        handler.postDelayed(wakeWatchTimeout, WAKE_WATCH_TIMEOUT_MS)

        mqttManager.publishWakeCommand { success ->
            if (!success) {
                runOnUiThread {
                    handler.removeCallbacks(wakeTimeout)
                    handler.removeCallbacks(wakeWatchTimeout)
                    awaitingWakeAck = false
                    isWaking = false
                    lastStatus = "Failed"
                }
            }
        }
    }

    private fun requestCheck() {
        if (esp32Status != "Online" || isChecking || isWaking) {
            return
        }

        isChecking = true
        lastStatus = "Waiting"
        checkBaselineUnix = lastPowerUnix ?: 0L
        checkStartedWallSec = System.currentTimeMillis() / 1000

        handler.removeCallbacks(checkTimeout)
        handler.postDelayed(checkTimeout, CHECK_TIMEOUT_MS)

        mqttManager.publishCheckCommand { success ->
            if (!success) {
                runOnUiThread {
                    handler.removeCallbacks(checkTimeout)
                    isChecking = false
                    lastStatus = "Check failed"
                }
            }
        }
    }

    private fun onPowerStatus(status: PcPowerStatus) {
        if (!applyPowerDisplay(status)) {
            return
        }

        if (isChecking && isFreshForRequest(status.checkedAtUnix, checkBaselineUnix, checkStartedWallSec)) {
            isChecking = false
            handler.removeCallbacks(checkTimeout)
            lastStatus = "Waiting"
        }

        if (isWaking && status.online && isFreshForRequest(status.checkedAtUnix, wakeBaselineUnix, wakeStartedWallSec)) {
            isWaking = false
            awaitingWakeAck = false
            lastStatus = "Waiting"
            handler.removeCallbacks(wakeTimeout)
            handler.removeCallbacks(wakeWatchTimeout)
        }
    }

    private fun applyPowerDisplay(status: PcPowerStatus): Boolean {
        if (status.checkedAtUnix == 0L) {
            if (lastPowerUnix == null) {
                pcStatus = if (status.online) "Online" else "Offline"
                lastCheckedLabel = "Last checked: time unknown"
            }
            return false
        }

        val previous = lastPowerUnix
        if (previous != null && status.checkedAtUnix < previous) {
            return false
        }

        lastPowerUnix = status.checkedAtUnix
        pcStatus = if (status.online) "Online" else "Offline"
        lastCheckedLabel = "Last checked ${formatCheckedAt(status.checkedAtUnix)}"
        return true
    }

    // Matches a result to the request that was in progress when it was sent.
    // A message created clearly before that request cannot finish it.
    // Timestamps still cannot tell apart a not-yet-seen retained message that
    // falls inside the clock-skew window, or two checks in the same second.
    private fun isFreshForRequest(
        checkedAtUnix: Long,
        baselineUnix: Long,
        requestStartedWallSec: Long
    ): Boolean {
        if (checkedAtUnix <= 0L || checkedAtUnix <= baselineUnix) {
            return false
        }

        val nowSec = System.currentTimeMillis() / 1000
        if (checkedAtUnix + CLOCK_SKEW_SEC < requestStartedWallSec) {
            return false
        }
        if (checkedAtUnix > nowSec + CLOCK_SKEW_SEC) {
            return false
        }
        return true
    }

    private fun formatCheckedAt(unixSeconds: Long): String {
        val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(unixSeconds * 1000))
        return formatted
    }
}

private const val WAKE_ACK_TIMEOUT_MS = 5_000L
private const val WAKE_WATCH_TIMEOUT_MS = 60_000L
private const val CHECK_TIMEOUT_MS = 10_000L
private const val CLOCK_SKEW_SEC = 60L

@Composable
fun WakeBridgeScreen(
    modifier: Modifier = Modifier,
    esp32Status: String = "Connecting...",
    pcStatus: String = "Unknown",
    lastCheckedLabel: String = "Not checked yet",
    lastStatus: String = "Waiting",
    isWaking: Boolean = false,
    isChecking: Boolean = false,
    onWakeClick: () -> Unit = {},
    onCheckClick: () -> Unit = {}
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
            pcStatus = pcStatus,
            lastCheckedLabel = lastCheckedLabel,
            lastStatus = lastStatus,
            isWaking = isWaking,
            isChecking = isChecking,
            bridgeOnline = esp32Status == "Online",
            onWakeClick = onWakeClick,
            onCheckClick = onCheckClick
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
    lastCheckedLabel: String,
    lastStatus: String,
    isWaking: Boolean,
    isChecking: Boolean,
    bridgeOnline: Boolean,
    onWakeClick: () -> Unit,
    onCheckClick: () -> Unit
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
                text = lastCheckedLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when {
                    !bridgeOnline -> "Bridge is offline"
                    isWaking -> "Waking..."
                    isChecking -> "Checking..."
                    lastStatus == "Sending" -> "Sending wake command..."
                    lastStatus == "Wake sent" -> "Wake command sent"
                    lastStatus == "Failed" -> "Failed to send wake command"
                    lastStatus == "Timeout" -> "No response from bridge"
                    lastStatus == "Not online" -> "PC did not come online"
                    lastStatus == "Check failed" -> "Failed to request status"
                    lastStatus == "Check timeout" -> "No status response"
                    else -> "Ready to wake"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onWakeClick,
                enabled = !isWaking && bridgeOnline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = when{
                        !bridgeOnline -> "Bridge offline"
                        isWaking -> "Waking..."
                        lastStatus == "Failed" || lastStatus == "Timeout" -> "Try again"
                        else -> "Wake PC"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onCheckClick,
                enabled = bridgeOnline && !isChecking && !isWaking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = when {
                        !bridgeOnline -> "Bridge offline"
                        isChecking -> "Checking..."
                        else -> "Check status"
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