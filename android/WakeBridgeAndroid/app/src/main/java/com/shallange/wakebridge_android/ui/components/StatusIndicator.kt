package com.shallange.wakebridge_android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusIndicator(
    status: String,
    emphasized: Boolean = true,
    prominent: Boolean = false
) {
    val statusColor = when (status) {
        "Online" -> Color(0xFF4CAF50)
        "Offline" -> Color(0xFFF44336)
        "Connecting..." -> Color(0xFFFFC107)
        else -> Color.Gray
    }
    val dotColor = if (emphasized) statusColor else statusColor.copy(alpha = 0.45f)

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (prominent) 12.dp else 10.dp)
                .background(
                    color = dotColor,
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = status,
            fontSize = if (prominent) 16.sp else 14.sp,
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
