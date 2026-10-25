package com.sensorpark.app.ui.diagnostics

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
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot

private val Green = Color(0xFF2E7D32)
private val BuenosAires = LatLng(-34.6037, -58.3816)

/** Lista de chequeos de la configuración de Firebase y Maps. */
@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel = hiltViewModel()) {
    val checks by vm.checks.collectAsStateWithLifecycle()
    val parkingLots by vm.parkingLots.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Diagnóstico de configuración", style = MaterialTheme.typography.headlineMedium)
        checks.forEach { CheckRow(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::sendTestPush) { Text("Enviar push de prueba") }
            OutlinedButton(onClick = vm::runChecks) { Text("Reintentar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::loadSeed) { Text("Cargar datos semilla") }
        }

        ParkingLots(parkingLots)

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
private fun ParkingLots(result: DataResult<List<NearbyParkingLot>>?) {
    Text("Estacionamientos en vivo", style = MaterialTheme.typography.titleSmall)
    when (result) {
        null -> Text("Cargando…", style = MaterialTheme.typography.bodySmall)
        is DataResult.Failure -> Text("Error: ${result.reason}", color = MaterialTheme.colorScheme.error)
        is DataResult.Success -> {
            if (result.value.isEmpty()) {
                Text("No hay estacionamientos: ¿se cargó la semilla?", style = MaterialTheme.typography.bodySmall)
            }
            result.value.forEach { (lot, distance) ->
                Column {
                    Text(
                        "${lot.name}: ${lot.freeSpots}/${lot.capacity} libres · a ${(distance / 100).toInt() / 10.0} km",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        lot.freeSpotsByType.entries.joinToString(" · ") { (type, free) -> "$type $free/${lot.capacityOf(type)}" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    val (mark, color) = when (check.status) {
        CheckStatus.OK -> "✓" to Green
        CheckStatus.ERROR -> "✗" to MaterialTheme.colorScheme.error
        CheckStatus.RUNNING -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
        CheckStatus.NOT_RUN -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Text("$mark ${check.name}", color = color, style = MaterialTheme.typography.bodyLarge)
        if (check.detail.isNotEmpty()) {
            Text(check.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
