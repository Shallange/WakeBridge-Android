package com.shallange.wakebridge_android.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PowerRing(
    color: Color,
    pulsing: Boolean,
    modifier: Modifier = Modifier,
    ringSize: Dp = 168.dp
) {
    val pulse = if (pulsing && ValueAnimator.areAnimatorsEnabled()) {
        val transition = rememberInfiniteTransition(label = "power-pulse")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "power-pulse-progress"
        )
        progress
    } else {
        null
    }

    Canvas(modifier = modifier.size(ringSize)) {
        val stroke = 8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - stroke * 2.4f

        drawCircle(
            color = Color(0xFF181B20),
            radius = radius + stroke,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.16f),
            radius = radius + stroke * 1.8f,
            center = center
        )
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        if (pulse != null) {
            drawCircle(
                color = color.copy(alpha = (1f - pulse) * 0.55f),
                radius = radius + stroke * (1.2f + pulse * 2.4f),
                center = center,
                style = Stroke(width = stroke * (1f - pulse * 0.45f), cap = StrokeCap.Round)
            )
        }

        val glyphStroke = stroke * 0.72f
        val symbolRadius = radius * 0.36f
        val symbolCenter = Offset(center.x, center.y + symbolRadius * 0.16f)
        drawArc(
            color = color,
            startAngle = -50f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(
                symbolCenter.x - symbolRadius,
                symbolCenter.y - symbolRadius
            ),
            size = Size(symbolRadius * 2f, symbolRadius * 2f),
            style = Stroke(width = glyphStroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(center.x, symbolCenter.y - symbolRadius * 1.05f),
            end = Offset(center.x, symbolCenter.y - symbolRadius * 0.15f),
            strokeWidth = glyphStroke,
            cap = StrokeCap.Round
        )
    }
}
