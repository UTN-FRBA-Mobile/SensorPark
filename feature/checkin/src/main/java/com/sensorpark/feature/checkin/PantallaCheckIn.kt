package com.sensorpark.feature.checkin

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.componentes.PantallaProvisoria

@Composable
fun PantallaCheckIn(reservaId: String) {
    PantallaProvisoria(
        nombre = "Check-in",
        detalle = "Pantalla provisoria · reserva $reservaId",
    )
}
