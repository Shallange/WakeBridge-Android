package com.shallange.wakebridge_android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4C8DFF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF181B20),
    surfaceVariant = Color(0xFF22262D),

    onPrimary = Color.White,
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315FA8)
)

@Composable
fun WakeBridgeAndroidTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}