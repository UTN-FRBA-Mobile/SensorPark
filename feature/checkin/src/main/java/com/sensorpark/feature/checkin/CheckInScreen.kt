package com.sensorpark.feature.checkin

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.components.PlaceholderScreen

@Composable
fun CheckInScreen(reservationId: String) {
    PlaceholderScreen(
        title = "Check-in",
        detail = "Pantalla provisoria · reserva $reservationId",
    )
}
