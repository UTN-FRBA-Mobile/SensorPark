package com.sensorpark.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF1565C0)
val PrimaryLightBlue = Color(0xFFA9C7FF)

val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    inversePrimary = PrimaryLightBlue,
    secondary = Color(0xFF545F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF00687A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFABEDFF),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    inversePrimary = PrimaryBlue,
    secondary = Color(0xFF81C995),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF394452),
    onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = Color(0xFFFFB95F),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFFCA8100),
    onTertiaryContainer = Color(0xFF3E2400),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF26282E),
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceContainerLowest = Color(0xFF0E0E11),
    surfaceContainerLow = Color(0xFF171C24),
    surfaceContainer = Color(0xFF1C1D22),
    surfaceContainerHigh = Color(0xFF26282E),
    surfaceContainerHighest = Color(0xFF32353C),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF2D3037),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    scrim = Color.Black,
)
