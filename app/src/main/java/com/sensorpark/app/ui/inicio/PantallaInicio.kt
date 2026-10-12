package com.sensorpark.app.ui.inicio

import androidx.compose.runtime.Composable
import com.sensorpark.app.navegacion.Ruta
import com.sensorpark.core.ui.componentes.Accion
import com.sensorpark.core.ui.componentes.PantallaProvisoria
import com.sensorpark.domain.model.EstadoSesion

/**
 * Inicio provisorio: índice para llegar a todas las pantallas. Todavía no filtra por rol;
 * eso se define cuando existan las pantallas reales de cada uno.
 */
@Composable
fun PantallaInicio(sesion: EstadoSesion.Activa, irA: (ruta: String) -> Unit) {
    PantallaProvisoria(
        nombre = "SensorPark",
        detalle = "${sesion.sesion.email} · rol ${sesion.rol}",
        acciones = listOf(
            Accion("Mapa") { irA(Ruta.Mapa.patron) },
            Accion("Detalle de estacionamiento") { irA(Ruta.DetalleEstacionamiento.crear("demo")) },
            Accion("Reservar") { irA(Ruta.Reservar.crear("demo")) },
            Accion("Mis reservas") { irA(Ruta.MisReservas.crear()) },
            Accion("Check-in") { irA(Ruta.CheckIn.crear("demo")) },
            Accion("Preferencias") { irA(Ruta.Preferencias.patron) },
            Accion("Administración") { irA(Ruta.Admin.patron) },
            Accion("Diagnóstico de configuración") { irA(Ruta.Diagnostico.patron) },
        ),
    )
}
