package com.sensorpark.app.ui.settings

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction

@Composable
fun SettingsScreen(onSignOut: () -> Unit) {
    PlaceholderScreen(
        title = "Preferencias",
        actions = listOf(ScreenAction("Cerrar sesión", onSignOut)),
    )
}
