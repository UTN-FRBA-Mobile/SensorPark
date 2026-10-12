package com.sensorpark.feature.mapa

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.componentes.Accion
import com.sensorpark.core.ui.componentes.PantallaProvisoria

@Composable
fun PantallaMapa(irADetalle: (estacionamientoId: String) -> Unit) {
    PantallaProvisoria(
        nombre = "Mapa",
        acciones = listOf(Accion("Ver detalle de un estacionamiento") { irADetalle("demo") }),
    )
}
