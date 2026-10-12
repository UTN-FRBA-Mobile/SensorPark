package com.sensorpark.feature.estacionamiento

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.componentes.Accion
import com.sensorpark.core.ui.componentes.PantallaProvisoria

@Composable
fun PantallaDetalleEstacionamiento(
    estacionamientoId: String,
    irAReservar: (estacionamientoId: String) -> Unit,
) {
    PantallaProvisoria(
        nombre = "Detalle de estacionamiento",
        detalle = "Pantalla provisoria · estacionamiento $estacionamientoId",
        acciones = listOf(Accion("Reservar un lugar") { irAReservar(estacionamientoId) }),
    )
}
