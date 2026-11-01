package com.sensorpark.feature.parking

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.AvailabilityBadge
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.FavoriteButton
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.OfflineBanner
import com.sensorpark.core.ui.components.PrimaryButton
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.openDirections
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.sensorpark.core.ui.R as CoreR

/**
 * Detalle de un estacionamiento (CU-03): datos, ocupación y el plano de lugares en vivo.
 * Tocar un lugar libre lleva a reservarlo.
 */
@Composable
fun ParkingLotDetailScreen(
    parkingLotId: String,
    onReserve: (parkingLotId: String) -> Unit,
    onReserveSpot: (parkingLotId: String, spot: Spot) -> Unit,
    vm: ParkingLotDetailViewModel = hiltViewModel(),
    spotsVm: SpotAvailabilityViewModel = hiltViewModel(),
) {
    LaunchedEffect(parkingLotId) {
        vm.load(parkingLotId)
        spotsVm.load(parkingLotId)
    }
    val result by vm.result.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    // El listener de los lugares vive sólo mientras la pantalla está visible.
    val spots by spotsVm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        when (val current = result) {
            null -> LoadingContent()
            is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
            is DataResult.Success -> Detail(
                lot = current.value,
                spots = spots,
                favorite = favorite,
                onToggleFavorite = vm::toggleFavorite,
                onRetrySpots = spotsVm::retry,
                onReserve = { onReserve(parkingLotId) },
                onReserveSpot = { onReserveSpot(parkingLotId, it) },
            )
        }
    }
}

private const val GRID_COLUMNS = 4

@Composable
private fun Detail(
    lot: ParkingLot,
    spots: SpotAvailabilityState,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRetrySpots: () -> Unit,
    onReserve: () -> Unit,
    onReserveSpot: (Spot) -> Unit,
) {
    val context = LocalContext.current
    val directionsUnavailable = stringResource(CoreR.string.directions_unavailable)

    // Las celdas se rearman sólo cuando llega una lista nueva.
    val cells = remember(spots) {
        when (spots) {
            is SpotAvailabilityState.Live -> spots.spots.map(Spot::toCell)
            is SpotAvailabilityState.Offline -> spots.spots.map(Spot::toCell)
            else -> emptyList()
        }
    }
    val rows = remember(cells) { cells.chunked(GRID_COLUMNS) }
    val visibleTypes = remember(lot) { SpotType.entries.filter { lot.capacityOf(it) > 0 } }
    val currentOnReserveSpot by rememberUpdatedState(onReserveSpot)
    val onCellClick = remember { { cell: SpotCell -> currentOnReserveSpot(cell.toSpot()) } }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(lot, spots, favorite, onToggleFavorite)
            OccupancyCard(lot)

            if (visibleTypes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.detail_by_type),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    visibleTypes.forEach { type ->
                        SpotTypeRow(
                            label = type.label(),
                            free = lot.freeSpotsOf(type),
                            capacity = lot.capacityOf(type),
                        )
                    }
                }
            }

            PlanHeader()

            when {
                spots is SpotAvailabilityState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                spots is SpotAvailabilityState.Failure -> {
                    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.plan_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetrySpots) { Text(stringResource(CoreR.string.action_retry)) }
                    }
                }
                rows.isEmpty() -> {
                    Text(
                        stringResource(R.string.plan_empty),
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { rowCells ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowCells.forEach { cell ->
                                    androidx.compose.runtime.key(cell.id) {
                                        SpotCellView(
                                            cell = cell,
                                            onReserve = onCellClick,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                repeat(GRID_COLUMNS - rowCells.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        if (!context.openDirections(lot.location, lot.name)) {
                            Toast.makeText(context, directionsUnavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) { Text(stringResource(CoreR.string.directions_action)) }
                PrimaryButton(
                    text = stringResource(if (lot.freeSpots > 0) R.string.detail_reserve else R.string.detail_no_free_spots),
                    onClick = onReserve,
                    enabled = lot.freeSpots > 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Header(lot: ParkingLot, spots: SpotAvailabilityState, favorite: Boolean, onToggleFavorite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lot.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            FavoriteButton(favorite = favorite, parkingLotName = lot.name, onToggle = onToggleFavorite)
        }
        Text(
            text = lot.address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnectionStatus(spots)
        if (spots is SpotAvailabilityState.Offline) {
            OfflineBanner(
                lastUpdatedText = spots.lastUpdate?.let(::formatTime),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OccupancyCard(lot: ParkingLot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.detail_free_of_total, lot.freeSpots, lot.capacity),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Occupancy(lot.freeSpots, lot.capacity)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Info(lot)
        }
    }
}

private val SpotTypeRowShape = RoundedCornerShape(12.dp)

@Composable
private fun SpotTypeRow(label: String, free: Int, capacity: Int) {
    Surface(
        shape = SpotTypeRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            AvailabilityBadge(free, capacity)
        }
    }
}

@Composable
private fun PlanHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.plan_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.plan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SpotLegend()
    }
}

/** "En vivo" con datos del servidor, o "Sin conexión" con la hora de los últimos datos recibidos. */
@Composable
private fun ConnectionStatus(state: SpotAvailabilityState) {
    val colors = SensorParkTheme.colors
    val (text, status) = when (state) {
        is SpotAvailabilityState.Live -> stringResource(R.string.spots_live) to colors.positive
        is SpotAvailabilityState.Offline -> {
            val time = state.lastUpdate?.let(::formatTime)
            val label = if (time == null) stringResource(R.string.spots_offline)
            else stringResource(R.string.spots_offline_since, time)
            label to colors.negative
        }
        // Mientras carga, o si falla sólo la lectura de los lugares, los datos del resumen siguen valiendo.
        else -> return
    }

    Surface(
        shape = CircleShape,
        color = status.container,
        contentColor = status.content,
        border = BorderStroke(1.dp, status.content.copy(alpha = 0.3f)),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).background(status.content, CircleShape))
            Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val pesoFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).apply {
    maximumFractionDigits = 0
}

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
            add(stringResource(R.string.detail_rate) to stringResource(R.string.detail_rate_value, pesoFormat.format(lot.hourlyRate)))
        }
    }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Barra con la proporción de lugares libres. */
@Composable
private fun Occupancy(free: Int, capacity: Int) {
    LinearProgressIndicator(
        progress = { if (capacity == 0) 0f else free.toFloat() / capacity },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
    )
}
