package com.sensorpark.app.ui.diagnostico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState

private val Verde = Color(0xFF2E7D32)
private val BuenosAires = LatLng(-34.6037, -58.3816)

/** Lista de chequeos de la configuración de Firebase y Maps. */
@Composable
fun PantallaDiagnostico(vm: DiagnosticoViewModel = hiltViewModel()) {
    val chequeos by vm.chequeos.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Diagnóstico de configuración", style = MaterialTheme.typography.headlineMedium)
        chequeos.forEach { FilaChequeo(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::enviarPushDePrueba) { Text("Enviar push de prueba") }
            OutlinedButton(onClick = vm::probar) { Text("Reintentar") }
        }

        Text("Maps", style = MaterialTheme.typography.titleSmall)
        Text(
            "Si el mapa se ve gris, falta MAPS_API_KEY en local.properties o está mal restringida.",
            style = MaterialTheme.typography.bodySmall,
        )
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(BuenosAires, 12f)
            },
        )
    }
}

@Composable
private fun FilaChequeo(chequeo: Chequeo) {
    val (marca, color) = when (chequeo.resultado) {
        Resultado.OK -> "✓" to Verde
        Resultado.ERROR -> "✗" to MaterialTheme.colorScheme.error
        Resultado.PROBANDO -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
        Resultado.SIN_PROBAR -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Text("$marca ${chequeo.nombre}", color = color, style = MaterialTheme.typography.bodyLarge)
        if (chequeo.detalle.isNotEmpty()) {
            Text(chequeo.detalle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
