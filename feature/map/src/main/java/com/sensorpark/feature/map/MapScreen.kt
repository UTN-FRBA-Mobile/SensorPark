package com.sensorpark.feature.map

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.ParkingLotCard
import com.sensorpark.core.ui.components.formatDistance
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.openDirections
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.ParkingLotFilter
import com.sensorpark.core.ui.R as CoreR

/**
 * Pestaña Buscar (CU-02): los estacionamientos cercanos en un mapa o en una lista, con filtros.
 * Busca desde la ubicación del teléfono; sin permiso muestra una zona por defecto y deja mover el
 * mapa para buscar en otro lado.
 */
@Composable
fun MapScreen(
    onOpenParkingLot: (parkingLotId: String) -> Unit,
    vm: NearbyParkingLotsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val filter by vm.filter.collectAsStateWithLifecycle()
    val favoritesOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val origin by vm.origin.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val locationFailed by vm.locationFailed.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()

    // El usuario pudo cambiar el permiso en Ajustes mientras la app estaba en segundo plano.
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    var askedForPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        askedForPermission = true
        vm.onPermissionResult(grants.values.any { it })
    }
    val permanentlyDenied = access != LocationAccess.GRANTED && context.locationPermanentlyDenied(askedForPermission)

    val directionsUnavailable = stringResource(CoreR.string.directions_unavailable)
    val onDirections: (ParkingLot) -> Unit = { lot ->
        if (!context.openDirections(lot.location, lot.name)) {
            Toast.makeText(context, directionsUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(origin, viewMode, onViewMode = vm::setViewMode)
        if (access != LocationAccess.GRANTED) {
            LocationNotice(
                message = stringResource(
                    if (permanentlyDenied) R.string.nearby_permission_settings else R.string.nearby_permission_rationale,
                ),
                action = stringResource(
                    if (permanentlyDenied) R.string.nearby_permission_open_settings else R.string.nearby_permission_allow,
                ),
                onAction = {
                    if (permanentlyDenied) context.openAppSettings() else permissionLauncher.launch(LOCATION_PERMISSIONS)
                },
            )
        } else if (locationFailed) {
            LocationNotice(
                message = stringResource(R.string.nearby_location_failed),
                action = stringResource(R.string.nearby_location_retry),
                onAction = vm::locate,
            )
        } else if (locating) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.nearby_locating), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        Filters(
            filter = filter,
            favoritesOnly = favoritesOnly,
            onToggleOnlyWithFreeSpots = vm::toggleOnlyWithFreeSpots,
            onToggleFavoritesOnly = vm::toggleFavoritesOnly,
            onSelectSpotType = vm::selectSpotType,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val current = result) {
                null -> LoadingContent()
                is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
                is DataResult.Success -> when (viewMode) {
                    ViewMode.MAP -> ParkingLotMap(
                        origin = origin,
                        lots = current.value,
                        selectedId = selectedId,
                        favoriteIds = favoriteIds,
                        myLocationEnabled = access == LocationAccess.GRANTED,
                        onSelect = vm::select,
                        onSearchHere = vm::searchHere,
                        onMyLocation = {
                            if (access == LocationAccess.GRANTED) vm.locate() else permissionLauncher.launch(LOCATION_PERMISSIONS)
                        },
                        onOpenParkingLot = onOpenParkingLot,
                        onDirections = onDirections,
                        onToggleFavorite = vm::toggleFavorite,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ViewMode.LIST -> ParkingLotList(
                        lots = current.value,
                        spotType = filter.spotType,
                        favoriteIds = favoriteIds,
                        onOpenParkingLot = onOpenParkingLot,
                        onDirections = onDirections,
                        onToggleFavorite = vm::toggleFavorite,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(origin: Origin, viewMode: ViewMode, onViewMode: (ViewMode) -> Unit) {
    val radius = formatDistance(NearbyParkingLotsViewModel.RADIUS_METERS)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when (origin.source) {
                    OriginSource.DEVICE -> stringResource(R.string.nearby_subtitle_device, radius)
                    OriginSource.DEFAULT_ZONE ->
                        stringResource(R.string.nearby_subtitle_default, radius, NearbyParkingLotsViewModel.DEFAULT_ZONE_NAME)
                    OriginSource.MAP_AREA -> stringResource(R.string.nearby_subtitle_area, radius)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = viewMode == ViewMode.MAP,
                onClick = { onViewMode(ViewMode.MAP) },
                label = { Text(stringResource(R.string.nearby_view_map)) },
                leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null) },
            )
            FilterChip(
                selected = viewMode == ViewMode.LIST,
                onClick = { onViewMode(ViewMode.LIST) },
                label = { Text(stringResource(R.string.nearby_view_list)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            )
        }
    }
}

/** Aviso con una acción, para pedir el permiso de ubicación o reintentar la lectura. */
@Composable
private fun LocationNotice(message: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun Filters(
    filter: ParkingLotFilter,
    favoritesOnly: Boolean,
    onToggleOnlyWithFreeSpots: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSelectSpotType: (SpotType) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "free") {
            FilterChip(
                selected = filter.onlyWithFreeSpots,
                onClick = onToggleOnlyWithFreeSpots,
                label = { Text(stringResource(R.string.nearby_filter_free)) },
            )
        }
        item(key = "favorites") {
            FilterChip(
                selected = favoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = { Text(stringResource(R.string.nearby_filter_favorites)) },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            )
        }
        items(FILTER_TYPES, key = { it.name }) { type ->
            FilterChip(
                selected = filter.spotType == type,
                onClick = { onSelectSpotType(type) },
                label = { Text(type.label()) },
            )
        }
    }
}

private val FILTER_TYPES = listOf(SpotType.PRIORITY, SpotType.MOTORCYCLE, SpotType.EV_CHARGING)

@Composable
private fun ParkingLotList(
    lots: List<NearbyParkingLot>,
    spotType: SpotType?,
    favoriteIds: Set<String>,
    onOpenParkingLot: (String) -> Unit,
    onDirections: (ParkingLot) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    if (lots.isEmpty()) {
        Text(
            stringResource(R.string.nearby_empty),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Los callbacks se recuerdan una sola vez: así una tarjeta que no cambió no se vuelve a dibujar.
    val open = rememberUpdatedState(onOpenParkingLot)
    val toggle = rememberUpdatedState(onToggleFavorite)
    val directions = rememberUpdatedState(onDirections)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(lots, key = { it.parkingLot.id }, contentType = { "parking-lot" }) { (lot, distance) ->
            Column {
                ParkingLotCard(
                    name = lot.name,
                    subtitle = "${formatDistance(distance)} · ${lot.address}",
                    freeSpots = lot.freeSpots,
                    capacity = lot.capacity,
                    onClick = { open.value(lot.id) },
                    footer = spotType?.let {
                        stringResource(R.string.nearby_free_of_type, it.label(), lot.freeSpotsOf(it), lot.capacityOf(it))
                    },
                    favorite = lot.id in favoriteIds,
                    onToggleFavorite = { toggle.value(lot.id) },
                )
                TextButton(onClick = { directions.value(lot) }) {
                    Text(stringResource(CoreR.string.directions_action))
                }
            }
        }
    }
}
