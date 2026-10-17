package com.sensorpark.feature.parking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.AvailabilityBadge
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.label
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Detalle de un estacionamiento: datos y ocupación total y por tipo de lugar, en vivo.
 * Base para T12, que suma el plano con cada lugar.
 */
@Composable
fun ParkingLotDetailScreen(
    parkingLotId: String,
    onReserve: (parkingLotId: String) -> Unit,
    vm: ParkingLotDetailViewModel = hiltViewModel(),
    spotsVm: SpotAvailabilityViewModel = hiltViewModel(),
) {
    LaunchedEffect(parkingLotId) {
        vm.load(parkingLotId)
        spotsVm.load(parkingLotId)
    }
    val result by vm.result.collectAsStateWithLifecycle()
    // El listener de los lugares vive sólo mientras la pantalla está visible.
    val spots by spotsVm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        when (val current = result) {
            null -> LoadingContent()
            is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
            is DataResult.Success -> Detail(current.value, spots, onReserve = { onReserve(parkingLotId) })
        }
    }
}

@Composable
private fun Detail(lot: ParkingLot, spots: SpotAvailabilityState, onReserve: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(lot.name, style = MaterialTheme.typography.headlineSmall)
            Text(lot.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ConnectionStatus(spots)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.detail_free_of_total, lot.freeSpots, lot.capacity),
                    style = MaterialTheme.typography.titleLarge,
                )
                Occupancy(lot.freeSpots, lot.capacity)
            }
        }

        Info(lot)

        Section(stringResource(R.string.detail_by_type)) {
            SpotType.entries.filter { lot.capacityOf(it) > 0 }.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type.label(), modifier = Modifier.weight(1f))
                    AvailabilityBadge(lot.freeSpotsOf(type), lot.capacityOf(type))
                }
                Occupancy(lot.freeSpotsOf(type), lot.capacityOf(type))
            }
        }

        Button(onClick = onReserve, enabled = lot.freeSpots > 0, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (lot.freeSpots > 0) R.string.detail_reserve else R.string.detail_no_free_spots))
        }
    }
}

/** "En vivo" con datos del servidor, o "Sin conexión" con la hora de los últimos datos recibidos. */
@Composable
private fun ConnectionStatus(state: SpotAvailabilityState) {
    val (text, color) = when (state) {
        is SpotAvailabilityState.Live -> stringResource(R.string.spots_live) to MaterialTheme.colorScheme.primary
        is SpotAvailabilityState.Offline -> {
            val time = state.lastUpdate?.let(::formatTime)
            val label = if (time == null) {
                stringResource(R.string.spots_offline)
            } else {
                stringResource(R.string.spots_offline_since, time)
            }
            label to MaterialTheme.colorScheme.error
        }
        // Mientras carga, o si falla sólo la lectura de los lugares, los datos del resumen siguen valiendo.
        else -> return
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(instant: Instant): String = timeFormat.format(instant)

@Composable
private fun Info(lot: ParkingLot) {
    val items = buildList {
        lot.schedule?.let {
            add(
                stringResource(R.string.detail_schedule) to
                    stringResource(R.string.detail_schedule_value, it.opensAt.toString(), it.closesAt.toString()),
            )
        }
        if (lot.toleranceMinutes > 0) {
            add(stringResource(R.string.detail_tolerance) to stringResource(R.string.detail_tolerance_value, lot.toleranceMinutes))
        }
        if (lot.hourlyRate > 0) {
            // La tarifa está en pesos argentinos.
            val pesoFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).apply { maximumFractionDigits = 0 }
            add(stringResource(R.string.detail_rate) to stringResource(R.string.detail_rate_value, pesoFormat.format(lot.hourlyRate)))
        }
    }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, value) ->
            Row {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Barra con la proporción de lugares libres. */
@Composable
private fun Occupancy(free: Int, capacity: Int) {
    LinearProgressIndicator(
        progress = { if (capacity == 0) 0f else free.toFloat() / capacity },
        modifier = Modifier.fillMaxWidth(),
    )
}
