package com.nickdegs.hush.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import com.nickdegs.hush.ui.theme.Blue
import com.nickdegs.hush.ui.theme.Cyan
import com.nickdegs.hush.ui.theme.Deep
import com.nickdegs.hush.ui.theme.Violet
import kotlin.math.cos
import kotlin.math.sin

/**
 * iOS LiquidBackground'un Compose karşılığı — animasyonlu cam/gradient zemin.
 * 3 büyük blob (violet/blue/cyan) yumuşakça dolaşır + üst overlay.
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "liquid")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxSize().background(Deep)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height

            drawBlob(
                Offset(w * (0.28f + 0.16f * sin(t * 0.22f)),
                       h * (0.22f + 0.06f * cos(t * 0.18f))),
                Violet.copy(alpha = 0.42f), w * 0.45f
            )
            drawBlob(
                Offset(w * (0.74f + 0.13f * cos(t * 0.19f)),
                       h * (0.34f + 0.07f * sin(t * 0.24f))),
                Blue.copy(alpha = 0.40f), w * 0.48f
            )
            drawBlob(
                Offset(w * (0.50f + 0.10f * sin(t * 0.16f)),
                       h * (0.82f + 0.06f * cos(t * 0.21f))),
                Cyan.copy(alpha = 0.30f), w * 0.42f
            )

            // Üst film overlay (cam hissi)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f),
                    )
                )
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlob(
    center: Offset,
    color: Color,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
