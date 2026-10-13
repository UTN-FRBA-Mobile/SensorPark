package com.sensorpark.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1565C0)
private val LightBlue = Color(0xFF90CAF9)

private val LightColors = lightColorScheme(primary = Blue)
private val DarkColors = darkColorScheme(primary = LightBlue)

/** Tema provisorio: colores, tipografía y formas definitivos los define T03. */
@Composable
fun SensorParkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
