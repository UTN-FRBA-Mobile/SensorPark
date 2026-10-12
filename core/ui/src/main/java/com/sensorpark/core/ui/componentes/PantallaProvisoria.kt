package com.sensorpark.core.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Botón de una pantalla provisoria: a dónde se puede ir desde ahí. */
data class Accion(val texto: String, val onClick: () -> Unit)

/**
 * Relleno de una pantalla que todavía no se implementó: muestra su nombre y botones
 * para seguir navegando. Cada responsable la reemplaza por la pantalla real.
 */
@Composable
fun PantallaProvisoria(
    nombre: String,
    detalle: String? = null,
    acciones: List<Accion> = emptyList(),
    contenido: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(nombre, style = MaterialTheme.typography.headlineMedium)
        Text(
            detalle ?: "Pantalla provisoria",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        contenido()
        acciones.forEach { accion ->
            FilledTonalButton(onClick = accion.onClick, modifier = Modifier.fillMaxWidth()) {
                Text(accion.texto)
            }
        }
    }
}
