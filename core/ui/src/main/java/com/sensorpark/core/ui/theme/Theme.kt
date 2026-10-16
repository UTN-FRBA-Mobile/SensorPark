package com.sensorpark.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1565C0)
private val LightBlue = Color(0xFF90CAF9)

private val LightColors = lightColorScheme(primary = Blue)
private val DarkColors = darkColorScheme(primary = LightBlue)

/** Tema provisorio: colores, tipografía y formas definitivos los define T03. */
@Composable
fun SensorParkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalSensorParkColors provides if (dark) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}

/** Acceso a lo que el tema agrega a Material: `SensorParkTheme.colors.positive`. */
object SensorParkTheme {
    val colors: SensorParkColors
        @Composable @ReadOnlyComposable
        get() = LocalSensorParkColors.current
}
