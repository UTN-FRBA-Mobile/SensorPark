package com.sensorpark.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.ParkingLotCard
import com.sensorpark.core.ui.components.formatDistance
import com.sensorpark.core.ui.components.label
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.ParkingLotFilter

/**
 * Pestaña Buscar: estacionamientos cercanos, en una lista con filtros. Base para T11, que suma
 * el mapa y la ubicación del teléfono.
 */
@Composable
fun MapScreen(
    onOpenParkingLot: (parkingLotId: String) -> Unit,
    vm: NearbyParkingLotsViewModel = hiltViewModel(),
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    R.string.nearby_subtitle,
                    formatDistance(NearbyParkingLotsViewModel.RADIUS_METERS),
                    NearbyParkingLotsViewModel.ORIGIN_NAME,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Filters(filter, vm::toggleOnlyWithFreeSpots, vm::selectSpotType)

        when (val current = result) {
            null -> LoadingContent()
            is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
            is DataResult.Success ->
                if (current.value.isEmpty()) {
                    Text(
                        stringResource(R.string.nearby_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.value, key = { it.parkingLot.id }) { (lot, distance) ->
                            val type = filter.spotType
                            ParkingLotCard(
                                name = lot.name,
                                subtitle = "${formatDistance(distance)} · ${lot.address}",
                                freeSpots = lot.freeSpots,
                                capacity = lot.capacity,
                                onClick = { onOpenParkingLot(lot.id) },
                                footer = type?.let {
                                    stringResource(
                                        R.string.nearby_free_of_type,
                                        it.label(),
                                        lot.freeSpotsOf(it),
                                        lot.capacityOf(it),
                                    )
                                },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun Filters(
    filter: ParkingLotFilter,
    onToggleOnlyWithFreeSpots: () -> Unit,
    onSelectSpotType: (SpotType) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = filter.onlyWithFreeSpots,
                onClick = onToggleOnlyWithFreeSpots,
                label = { Text(stringResource(R.string.nearby_filter_free)) },
            )
        }
        items(listOf(SpotType.PRIORITY, SpotType.MOTORCYCLE, SpotType.EV_CHARGING)) { type ->
            FilterChip(
                selected = filter.spotType == type,
                onClick = { onSelectSpotType(type) },
                label = { Text(type.label()) },
            )
        }
    }
}
