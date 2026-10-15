package com.sensorpark.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.app.ui.profile.CategorySelector
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction
import com.sensorpark.domain.model.Role

/** Preferencias. La categoría de prioridad sólo la ve un conductor. */
@Composable
fun SettingsScreen(role: Role?, onSignOut: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val category by vm.category.collectAsStateWithLifecycle()

    PlaceholderScreen(
        title = "Preferencias",
        actions = listOf(ScreenAction("Cerrar sesión", onSignOut)),
    ) {
        val current = category
        if (role == Role.DRIVER && current != null) {
            CategorySelector(selected = current, onSelect = vm::setCategory)
        }
    }
}
