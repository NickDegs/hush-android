package com.nickdegs.hush.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * iOS Liquid Glass'ın Android karşılığı — Material 3 dynamic colors + violet brand.
 * Android 12+ cihazlarda kullanıcının duvar kağıdına göre tema renkleri uyarlanır
 * (Material You); diğerlerinde Hush mor/mavi marka teması kullanılır.
 */

internal val Violet = Color(0xFF7C3AED)
internal val Blue   = Color(0xFF2563EB)
internal val Cyan   = Color(0xFF22D3EE)
internal val Deep   = Color(0xFF0D0B1A)
internal val Surface = Color(0xFF1B1140)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Violet.copy(alpha = 0.2f),
    onPrimaryContainer = Color.White,
    secondary = Cyan,
    onSecondary = Color.Black,
    tertiary = Blue,
    background = Deep,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
    surfaceVariant = Surface.copy(alpha = 0.6f),
    onSurfaceVariant = Color.White.copy(alpha = 0.8f),
    outline = Color.White.copy(alpha = 0.18f),
)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Blue,
    tertiary = Cyan,
)

@Composable
fun HushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,    // markayı koru — Material You opsiyonel
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else darkColorScheme(primary = Violet)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HushTypography,
        content = content
    )
}
