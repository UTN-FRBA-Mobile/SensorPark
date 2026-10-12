package com.sensorpark.feature.reservas

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.componentes.Accion
import com.sensorpark.core.ui.componentes.PantallaProvisoria

@Composable
fun PantallaReservar(estacionamientoId: String, irAMisReservas: () -> Unit) {
    PantallaProvisoria(
        nombre = "Reservar",
        detalle = "Pantalla provisoria · estacionamiento $estacionamientoId",
        acciones = listOf(Accion("Ir a mis reservas", irAMisReservas)),
    )
}

/** @param reservaId reserva a destacar cuando se llega por el deep link `sensorpark://reserva/{id}`. */
@Composable
fun PantallaMisReservas(reservaId: String?, irACheckIn: (reservaId: String) -> Unit) {
    PantallaProvisoria(
        nombre = "Mis reservas",
        detalle = if (reservaId == null) null else "Pantalla provisoria · abierta en la reserva $reservaId",
        acciones = listOf(Accion("Hacer check-in") { irACheckIn(reservaId ?: "demo") }),
    )
}
