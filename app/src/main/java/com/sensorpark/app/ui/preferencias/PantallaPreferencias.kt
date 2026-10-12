package com.sensorpark.app.ui.preferencias

import androidx.compose.runtime.Composable
import com.sensorpark.core.ui.componentes.Accion
import com.sensorpark.core.ui.componentes.PantallaProvisoria

@Composable
fun PantallaPreferencias(onSalir: () -> Unit) {
    PantallaProvisoria(
        nombre = "Preferencias",
        acciones = listOf(Accion("Cerrar sesión", onSalir)),
    )
}
