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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.sensorpark.core.ui.components.PrimaryButton
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = lot.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = lot.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionStatus(spots)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
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

        Section(stringResource(R.string.detail_by_type)) {
            SpotType.entries.filter { lot.capacityOf(it) > 0 }.forEach { type ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = type.label(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        AvailabilityBadge(lot.freeSpotsOf(type), lot.capacityOf(type))
                    }
                }
            }
        }

        PrimaryButton(
            text = stringResource(if (lot.freeSpots > 0) R.string.detail_reserve else R.string.detail_no_free_spots),
            onClick = onReserve,
            enabled = lot.freeSpots > 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "En vivo" con datos del servidor, o "Sin conexión" con la hora de los últimos datos recibidos. */
@Composable
private fun ConnectionStatus(state: SpotAvailabilityState) {
    val isLive = state is SpotAvailabilityState.Live
    val isOffline = state is SpotAvailabilityState.Offline
    if (!isLive && !isOffline) return

    val text = when (state) {
        is SpotAvailabilityState.Live -> stringResource(R.string.spots_live)
        is SpotAvailabilityState.Offline -> {
            val time = state.lastUpdate?.let(::formatTime)
            if (time == null) stringResource(R.string.spots_offline)
            else stringResource(R.string.spots_offline_since, time)
        }
        else -> return
    }

    val (bg, fg, border) = if (isLive) {
        Triple(Color(0xFF172E22), Color(0xFF86E49D), Color(0x4D22C55E))
    } else {
        Triple(Color(0xFF3E1818), Color(0xFFF2B8B5), Color(0x4DEF4444))
    }

    Surface(
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(fg, CircleShape),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
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
            val pesoFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).apply { maximumFractionDigits = 0 }
            add(stringResource(R.string.detail_rate) to stringResource(R.string.detail_rate_value, pesoFormat.format(lot.hourlyRate)))
        }
    }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

/** Barra con la proporción de lugares libres. */
@Composable
private fun Occupancy(free: Int, capacity: Int) {
    LinearProgressIndicator(
        progress = { if (capacity == 0) 0f else free.toFloat() / capacity },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
}
