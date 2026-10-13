package com.sensorpark.app.ui.home

import androidx.compose.runtime.Composable
import com.sensorpark.app.navigation.Route
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction
import com.sensorpark.domain.model.SessionState

/**
 * Inicio provisorio: índice para llegar a todas las pantallas. Todavía no filtra por rol;
 * eso se define cuando existan las pantallas reales de cada uno.
 */
@Composable
fun HomeScreen(state: SessionState.SignedIn, navigateTo: (route: String) -> Unit) {
    PlaceholderScreen(
        title = "SensorPark",
        detail = "${state.session.email} · rol ${state.role}",
        actions = listOf(
            ScreenAction("Mapa") { navigateTo(Route.Map.pattern) },
            ScreenAction("Detalle de estacionamiento") { navigateTo(Route.ParkingLotDetail.create("demo")) },
            ScreenAction("Reservar") { navigateTo(Route.Reserve.create("demo")) },
            ScreenAction("Mis reservas") { navigateTo(Route.MyReservations.create()) },
            ScreenAction("Check-in") { navigateTo(Route.CheckIn.create("demo")) },
            ScreenAction("Preferencias") { navigateTo(Route.Settings.pattern) },
            ScreenAction("Administración") { navigateTo(Route.Admin.pattern) },
            ScreenAction("Diagnóstico de configuración") { navigateTo(Route.Diagnostics.pattern) },
        ),
    )
}
