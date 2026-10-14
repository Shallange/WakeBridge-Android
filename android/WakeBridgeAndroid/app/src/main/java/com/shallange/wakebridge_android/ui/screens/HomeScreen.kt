package com.shallange.wakebridge_android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shallange.wakebridge_android.ui.components.BridgeStatusCard
import com.shallange.wakebridge_android.ui.components.PcCard
import com.shallange.wakebridge_android.ui.theme.WakeBridgeAndroidTheme

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            pcStatusFresh = pcStatusFresh,
            onWakeClick = onWakeClick,
            onCheckClick = onCheckClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        BridgeStatusCard(
            esp32Status = esp32Status
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
