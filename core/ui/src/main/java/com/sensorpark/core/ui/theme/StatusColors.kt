package com.sensorpark.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Fondo y texto de una pastilla o celda de estado. */
@Immutable
data class StatusColor(val container: Color, val content: Color)

/**
 * Colores con significado, iguales en toda la app: verde para lo disponible, ámbar para lo que
 * está por agotarse o reservado, rojo para lo lleno u ocupado y gris para lo que no funciona.
 * Nunca deberían ser la única pista: siempre van con un texto o un ícono.
 */
@Immutable
data class SensorParkColors(
    val positive: StatusColor,
    val warning: StatusColor,
    val negative: StatusColor,
    val neutral: StatusColor,
)

internal val LightStatusColors = SensorParkColors(
    positive = StatusColor(Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    warning = StatusColor(Color(0xFFFFE0B2), Color(0xFF7A3E00)),
    negative = StatusColor(Color(0xFFFFCDD2), Color(0xFF8E0000)),
    neutral = StatusColor(Color(0xFFE0E0E0), Color(0xFF424242)),
)

internal val DarkStatusColors = SensorParkColors(
    positive = StatusColor(Color(0xFF1E4620), Color(0xFFC8E6C9)),
    warning = StatusColor(Color(0xFF5C3A0A), Color(0xFFFFE0B2)),
    negative = StatusColor(Color(0xFF5F1A1A), Color(0xFFFFCDD2)),
    neutral = StatusColor(Color(0xFF3A3A3A), Color(0xFFE0E0E0)),
)

/** Lo provee SensorParkTheme. Fuera del tema (por ejemplo en un preview suelto) vale la versión clara. */
val LocalSensorParkColors = staticCompositionLocalOf { LightStatusColors }
