package com.shallange.wakebridge_android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shallange.wakebridge_android.ui.components.PowerRing
import com.shallange.wakebridge_android.ui.theme.WakeBridgeAndroidTheme

private val OnlineColor = Color(0xFF3DDC97)
private val OfflineColor = Color(0xFFD16B6B)
private val UnknownColor = Color(0xFF8E97A6)
private val WakingColor = Color(0xFF4C8DFF)
private val ConnectingColor = Color(0xFFFFC107)

@Composable
fun WakeBridgeScreen(
    modifier: Modifier = Modifier,
    esp32Status: String = "Connecting...",
    pcStatus: String = "Unknown",
    lastCheckedLabel: String = "Not checked yet",
    lastStatus: String = "Waiting",
    isWaking: Boolean = false,
    isChecking: Boolean = false,
    pcStatusFresh: Boolean = false,
    onWakeClick: () -> Unit = {},
    onCheckClick: () -> Unit = {}
) {
    val bridgeOnline = esp32Status == "Online"
    val stage = when {
        isWaking -> PowerStage.Waking
        pcStatus == "Online" -> PowerStage.Online
        pcStatus == "Offline" -> PowerStage.Offline
        else -> PowerStage.Unknown
    }
    val ringColor = stageColor(stage, pcStatusFresh)
    val caption = stageCaption(
        esp32Status = esp32Status,
        isWaking = isWaking,
        isChecking = isChecking,
        lastStatus = lastStatus,
        lastCheckedLabel = lastCheckedLabel
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val ringSize = maxOf(
            112.dp,
            minOf(168.dp, maxWidth - 96.dp, maxHeight * 0.34f)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WakeBridge",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                BridgeIndicator(esp32Status = esp32Status)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PC STATUS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                PowerRing(
                    color = ringColor,
                    pulsing = stage == PowerStage.Waking,
                    ringSize = ringSize
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stage.label,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = ringColor,
                    modifier = Modifier.semantics {
                        contentDescription = "PC ${stage.label}"
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = caption,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onWakeClick,
                    enabled = !isWaking && bridgeOnline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        text = "Wake PC",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCheckClick,
                    enabled = bridgeOnline && !isChecking && !isWaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        text = "Check status",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgeIndicator(
    esp32Status: String
) {
    val color = when (esp32Status) {
        "Online" -> OnlineColor
        "Offline" -> OfflineColor
        else -> ConnectingColor
    }
    val label = when (esp32Status) {
        "Online" -> "Online"
        "Offline" -> "Offline"
        else -> "Connecting"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "Bridge $label"
        }
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Bridge · $label",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class PowerStage(val label: String) {
    Online("Online"),
    Offline("Offline"),
    Unknown("Unknown"),
    Waking("Waking")
}

private fun stageColor(stage: PowerStage, fresh: Boolean): Color {
    val color = when (stage) {
        PowerStage.Online -> OnlineColor
        PowerStage.Offline -> OfflineColor
        PowerStage.Unknown -> UnknownColor
        PowerStage.Waking -> WakingColor
    }
    val stale = (stage == PowerStage.Online || stage == PowerStage.Offline) && !fresh
    return if (stale) color.copy(alpha = 0.45f) else color
}

private fun stageCaption(
    esp32Status: String,
    isWaking: Boolean,
    isChecking: Boolean,
    lastStatus: String,
    lastCheckedLabel: String
): String {
    return when {
        isChecking && esp32Status == "Online" -> "Checking..."
        isWaking && lastStatus == "Sending" -> "Sending wake command..."
        isWaking -> "Waiting for the PC"
        lastStatus == "Failed" -> "Failed to send wake command. Try again."
        lastStatus == "Timeout" -> "No response from bridge. Try again."
        lastStatus == "Not online" -> "PC did not come online"
        lastStatus == "Check failed" -> "Failed to request status"
        lastStatus == "Check timeout" -> "No status response"
        else -> lastCheckedLabel
    }
}

@Preview(showBackground = true)
@Composable
fun WakeBridgeScreenPreview() {
    WakeBridgeAndroidTheme {
        WakeBridgeScreen(
            esp32Status = "Online",
            pcStatus = "Offline",
            lastCheckedLabel = "Last known at 09:41",
            pcStatusFresh = false
        )
    }
}
