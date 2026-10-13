package com.sensorpark.feature.reservations

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction

@Composable
fun ReserveScreen(parkingLotId: String, onOpenMyReservations: () -> Unit) {
    PlaceholderScreen(
        title = "Reservar",
        detail = "Pantalla provisoria · estacionamiento $parkingLotId",
        actions = listOf(ScreenAction("Ir a mis reservas", onOpenMyReservations)),
    )
}

/** @param reservationId reserva a destacar cuando se llega por el deep link `sensorpark://reservation/{id}`. */
@Composable
fun MyReservationsScreen(reservationId: String?, onCheckIn: (reservationId: String) -> Unit) {
    PlaceholderScreen(
        title = "Mis reservas",
        detail = if (reservationId == null) null else "Pantalla provisoria · abierta en la reserva $reservationId",
        actions = listOf(ScreenAction("Hacer check-in") { onCheckIn(reservationId ?: "demo") }),
    )
}
