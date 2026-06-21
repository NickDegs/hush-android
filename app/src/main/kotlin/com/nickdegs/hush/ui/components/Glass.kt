package com.nickdegs.hush.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass cam yüzey — Compose'da real-time blur Android 12+'ta sınırlı,
 * onun yerine semi-transparent gradient + ince beyaz stroke ile çok benzer
 * görsel etki veriyoruz.
 */
fun Modifier.glassCard(
    cornerRadius: Int = 20,
    tintAlpha: Float = 0.06f,
    strokeAlpha: Float = 0.16f,
): Modifier = composedShape(RoundedCornerShape(cornerRadius.dp), tintAlpha, strokeAlpha)

fun Modifier.glassCapsule(
    tintAlpha: Float = 0.06f,
    strokeAlpha: Float = 0.16f,
): Modifier = composedShape(androidx.compose.foundation.shape.CircleShape, tintAlpha, strokeAlpha)

private fun Modifier.composedShape(shape: Shape, tintAlpha: Float, strokeAlpha: Float): Modifier =
    this.clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = tintAlpha + 0.06f),
                    Color.White.copy(alpha = tintAlpha),
                )
            ),
            shape = shape
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = strokeAlpha + 0.10f),
                    Color.White.copy(alpha = strokeAlpha * 0.4f),
                )
            ),
            shape = shape
        )
