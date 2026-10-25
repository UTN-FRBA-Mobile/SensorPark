package com.sensorpark.feature.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.sensorpark.core.ui.components.AvailabilityBadge
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.ParkingLotCard
import com.sensorpark.core.ui.components.formatDistance
import com.sensorpark.core.ui.components.label
import com.sensorpark.domain.geo.distanceMeters
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.ParkingLotFilter

private const val ZOOM = 14.5f

/** Si el usuario movió el mapa más que esto desde donde se buscó, se le ofrece buscar ahí. */
private const val SEARCH_HERE_METERS = 400.0

/**
 * Pestaña Buscar (CU-02): los estacionamientos cercanos en un mapa o en una lista, desde la
 * ubicación del teléfono. El permiso se pide recién acá, explicando para qué, y si el usuario lo
 * niega la pantalla sigue andando desde una zona por defecto.
 */
@Composable
fun MapScreen(
    onOpenParkingLot: (parkingLotId: String) -> Unit,
    vm: NearbyParkingLotsViewModel = hiltViewModel(),
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val origin by vm.origin.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val onlyFavorites by vm.onlyFavorites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Cada vez que el usuario toca "Usar mi ubicación" se vuelve a explicar y a pedir el permiso.
    var permissionRequests by rememberSaveable { mutableIntStateOf(0) }

    LocationPermission(access = access, requestKey = permissionRequests, onResult = vm::onPermissionResult)

    Column(Modifier.fillMaxSize()) {
        Header(origin = origin, mode = mode, onToggleMode = vm::toggleMode)
        if (locating) LinearProgressIndicator(Modifier.fillMaxWidth())
        Filters(filter, onlyFavorites, vm::toggleOnlyWithFreeSpots, vm::toggleOnlyFavorites, vm::selectSpotType)
        LocationNotice(
            access = access,
            onAskAgain = { permissionRequests++ },
            onOpenSettings = { context.openAppSettings() },
        )

        Box(Modifier.weight(1f)) {
            when (val current = result) {
                null -> LoadingContent()
                is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
                is DataResult.Success -> when (mode) {
                    ViewMode.MAP -> ParkingMap(
                        lots = current.value,
                        origin = origin,
                        // Se mira el permiso real y no sólo el estado: si el usuario lo revocó desde
                        // los ajustes con la app abierta, prender el punto azul tiraría una excepción.
                        showMyLocation = access == LocationAccess.GRANTED && context.hasLocationPermission(),
                        selected = selected,
                        onSelect = vm::select,
                        onSearchHere = vm::searchHere,
                        onOpen = onOpenParkingLot,
                        onDirections = { context.openDirections(it) },
                    )
                    ViewMode.LIST -> ParkingList(current.value, filter, favorites, onOpenParkingLot, vm::toggleFavorite)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(origin: SearchOrigin, mode: ViewMode, onToggleMode: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = stringResource(R.string.nearby_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (origin.fromUser) {
                    stringResource(R.string.nearby_subtitle_user, formatDistance(NearbyParkingLotsViewModel.RADIUS_METERS))
                } else {
                    stringResource(
                        R.string.nearby_subtitle,
                        formatDistance(NearbyParkingLotsViewModel.RADIUS_METERS),
                        stringResource(R.string.nearby_this_area),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SingleChoiceSegmentedButtonRow {
            ViewMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { if (mode != option) onToggleMode() },
                    shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                    icon = { SegmentedButtonDefaults.Icon(active = mode == option) },
                    label = {
                        Text(
                            text = stringResource(if (option == ViewMode.MAP) R.string.nearby_mode_map else R.string.nearby_mode_list),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (mode == option) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Filters(
    filter: ParkingLotFilter,
    onlyFavorites: Boolean,
    onToggleOnlyWithFreeSpots: () -> Unit,
    onToggleOnlyFavorites: () -> Unit,
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
        item {
            FilterChip(
                selected = onlyFavorites,
                onClick = onToggleOnlyFavorites,
                label = { Text(stringResource(R.string.nearby_filter_favorites)) },
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

/** El aviso cuando no hay ubicación: qué se está mostrando y cómo cambiarlo. */
@Composable
private fun LocationNotice(access: LocationAccess, onAskAgain: () -> Unit, onOpenSettings: () -> Unit) {
    if (access != LocationAccess.DENIED && access != LocationAccess.DENIED_FOREVER) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    if (access == LocationAccess.DENIED_FOREVER) R.string.location_denied_forever
                    else R.string.location_denied,
                    NearbyParkingLotsViewModel.DEFAULT_ORIGIN_NAME,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (access == LocationAccess.DENIED_FOREVER) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.location_open_settings)) }
            } else {
                TextButton(onClick = onAskAgain) { Text(stringResource(R.string.location_ask_again)) }
            }
        }
    }
}

@Composable
private fun ParkingMap(
    lots: List<NearbyParkingLot>,
    origin: SearchOrigin,
    showMyLocation: Boolean,
    selected: NearbyParkingLot?,
    onSelect: (String?) -> Unit,
    onSearchHere: (Coordinates) -> Unit,
    onOpen: (String) -> Unit,
    onDirections: (Coordinates) -> Unit,
) {
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origin.coordinates.toLatLng(), ZOOM)
    }
    val scope = rememberCoroutineScope()
    val statusColors = SensorParkTheme.colors
    val density = LocalDensity.current.density
    // Cuando cambia desde dónde se busca (llegó la ubicación, o se buscó en otra zona), el mapa va ahí.
    LaunchedEffect(origin) {
        camera.animate(CameraUpdateFactory.newLatLngZoom(origin.coordinates.toLatLng(), ZOOM))
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            // El punto azul sólo con permiso: sin él, Maps tira una excepción.
            properties = MapProperties(isMyLocationEnabled = showMyLocation),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
            ),
            onMapClick = { onSelect(null) },
        ) {
            // `key`: cuando la lista cambia (un filtro, otra zona), cada marcador sigue siendo el
            // de su estacionamiento, en vez de que Compose los reasigne por posición.
            lots.forEach { nearby -> key(nearby.parkingLot.id) {
                val lot = nearby.parkingLot
                val fullLabel = stringResource(R.string.nearby_marker_full)
                val freeLabel = stringResource(R.string.nearby_marker_free, lot.freeSpots)
                // Los mismos colores que la pastilla de la lista: verde, ámbar o rojo.
                val icon = remember(lot.freeSpots, lot.capacity, statusColors, freeLabel) {
                    availabilityMarker(lot.freeSpots, lot.capacity, statusColors, density, fullLabel, freeLabel)
                }
                Marker(
                    state = rememberMarkerState(key = lot.id, position = lot.location.toLatLng()),
                    icon = icon,
                    // La punta de la pastilla, no su centro, es la que va sobre el estacionamiento.
                    anchor = Offset(0.5f, 1f),
                    title = lot.name,
                    contentDescription = stringResource(R.string.nearby_marker_description, lot.name, lot.freeSpots),
                    onClick = {
                        onSelect(lot.id)
                        true
                    },
                )
            } }
        }

        val target = camera.position.target
        val movedAway = !camera.isMoving &&
            distanceMeters(Coordinates(target.latitude, target.longitude), origin.coordinates) > SEARCH_HERE_METERS
        if (movedAway) {
            FilledTonalButton(
                onClick = { onSearchHere(Coordinates(target.latitude, target.longitude)) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = CircleShape,
            ) { Text(stringResource(R.string.nearby_search_here)) }
        }

        if (showMyLocation) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        camera.animate(CameraUpdateFactory.newLatLngZoom(origin.coordinates.toLatLng(), ZOOM))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.location_ask_again),
                )
            }
        }

        selected?.let { nearby ->
            SelectedLot(
                nearby = nearby,
                onOpen = { onOpen(nearby.parkingLot.id) },
                onDirections = { onDirections(nearby.parkingLot.location) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

/** La tarjeta del marcador tocado, con lo que se puede hacer desde ahí. */
@Composable
private fun SelectedLot(
    nearby: NearbyParkingLot,
    onOpen: () -> Unit,
    onDirections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lot = nearby.parkingLot
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = lot.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                AvailabilityBadge(free = lot.freeSpots, capacity = lot.capacity)
            }
            Text(
                text = "${formatDistance(nearby.distanceMeters)} · ${lot.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.nearby_open_detail), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDirections,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.nearby_directions))
                }
            }
        }
    }
}

@Composable
private fun ParkingList(
    lots: List<NearbyParkingLot>,
    filter: ParkingLotFilter,
    favorites: Set<String>,
    onOpen: (String) -> Unit,
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
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(lots, key = { it.parkingLot.id }) { (lot, distance) ->
            val type = filter.spotType
            ParkingLotCard(
                name = lot.name,
                subtitle = "${formatDistance(distance)} · ${lot.address}",
                freeSpots = lot.freeSpots,
                capacity = lot.capacity,
                onClick = { onOpen(lot.id) },
                favorite = lot.id in favorites,
                onToggleFavorite = { onToggleFavorite(lot.id) },
                modifier = Modifier.semantics { contentDescription = lot.name },
                footer = type?.let {
                    stringResource(R.string.nearby_free_of_type, it.label(), lot.freeSpotsOf(it), lot.capacityOf(it))
                },
            )
        }
    }
}

/**
 * Pide el permiso de ubicación en contexto: al llegar a Buscar, después de explicar para qué es.
 * Si lo niega, el aviso de la pantalla ofrece volver a pedirlo: cada pedido sube [requestKey].
 */
@Composable
private fun LocationPermission(
    access: LocationAccess,
    requestKey: Int,
    onResult: (granted: Boolean, canAskAgain: Boolean) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    var explaining by rememberSaveable(requestKey) {
        mutableStateOf(access == LocationAccess.UNKNOWN || requestKey > 0)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        // Con la ubicación aproximada alcanza para buscar cerca.
        val granted = grants.values.any { it }
        val canAskAgain = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: true
        onResult(granted, canAskAgain)
    }

    if (!explaining) return
    AlertDialog(
        onDismissRequest = {
            explaining = false
            onResult(false, true)
        },
        title = { Text(stringResource(R.string.location_rationale_title)) },
        text = { Text(stringResource(R.string.location_rationale_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    explaining = false
                    launcher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
            ) { Text(stringResource(R.string.location_allow)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    explaining = false
                    onResult(false, true)
                },
            ) { Text(stringResource(R.string.location_not_now)) }
        },
    )
}

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

private fun Context.hasLocationPermission(): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "Cómo llegar": un Intent implícito, que abre Google Maps si está, o el navegador si no. */
private fun Context.openDirections(destination: Coordinates) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(directionsUrl(destination)))) }
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
