package com.sensorpark.feature.map

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction

@Composable
fun MapScreen(onOpenParkingLot: (parkingLotId: String) -> Unit) {
    PlaceholderScreen(
        title = "Mapa",
        actions = listOf(ScreenAction("Ver detalle de un estacionamiento") { onOpenParkingLot("demo") }),
    )
}
