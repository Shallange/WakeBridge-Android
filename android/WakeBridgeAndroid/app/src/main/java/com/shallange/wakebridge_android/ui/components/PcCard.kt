package com.shallange.wakebridge_android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PcCard(
    pcStatus: String,
    lastCheckedLabel: String,
    lastStatus: String,
    isWaking: Boolean,
    isChecking: Boolean,
    bridgeOnline: Boolean,
    pcStatusFresh: Boolean,
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
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PC",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                StatusIndicator(
                    status = pcStatus,
                    emphasized = pcStatusFresh,
                    prominent = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    !bridgeOnline -> "Bridge is offline"
                    isWaking -> "Waking..."
                    isChecking -> "Checking..."
                    lastStatus == "Sending" -> "Sending wake command..."
                    lastStatus == "Wake sent" -> "Wake command sent"
                    lastStatus == "Failed" -> "Failed to send wake command. Try again."
                    lastStatus == "Timeout" -> "No response from bridge. Try again."
                    lastStatus == "Not online" -> "PC did not come online"
                    lastStatus == "Check failed" -> "Failed to request status"
                    lastStatus == "Check timeout" -> "No status response"
                    else -> "Ready to wake"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lastCheckedLabel,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onWakeClick,
                enabled = !isWaking && bridgeOnline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Wake PC",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCheckClick,
                enabled = bridgeOnline && !isChecking && !isWaking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
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
