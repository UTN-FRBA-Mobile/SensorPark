package com.sensorpark.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

@Composable
fun SensorParkTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColorScheme
        else -> LightColorScheme
    }
    CompositionLocalProvider(LocalSensorParkColors provides if (dark) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = SensorParkShapes,
            typography = SensorParkTypography,
            content = content,
        )
    }
}

object SensorParkTheme {
    val colors: SensorParkColors
        @Composable @ReadOnlyComposable
        get() = LocalSensorParkColors.current
}
