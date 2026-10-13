package com.sensorpark.feature.parking

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction

@Composable
fun ParkingLotDetailScreen(
    parkingLotId: String,
    onReserve: (parkingLotId: String) -> Unit,
) {
    PlaceholderScreen(
        title = "Detalle de estacionamiento",
        detail = "Pantalla provisoria · estacionamiento $parkingLotId",
        actions = listOf(ScreenAction("Reservar un lugar") { onReserve(parkingLotId) }),
    )
}
