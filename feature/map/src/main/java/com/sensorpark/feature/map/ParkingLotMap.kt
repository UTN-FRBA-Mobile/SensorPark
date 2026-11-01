package com.sensorpark.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.sensorpark.core.ui.components.FavoriteButton
import com.sensorpark.core.ui.components.formatDistance
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.geo.distanceMeters
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.core.ui.R as CoreR

private const val ZOOM = 14f

/** Cuánto hay que mover el mapa para ofrecer "Buscar en esta zona". */
private const val MOVE_THRESHOLD_METERS = 300.0

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

/** Mapa de la pestaña Buscar. Si se arrastra lejos del origen, ofrece buscar en esa zona. */
@Composable
internal fun ParkingLotMap(
    origin: Origin,
    lots: List<NearbyParkingLot>,
    selectedId: String?,
    favoriteIds: Set<String>,
    myLocationEnabled: Boolean,
    onSelect: (String?) -> Unit,
    onSearchHere: (Coordinates) -> Unit,
    onMyLocation: () -> Unit,
    onOpenParkingLot: (String) -> Unit,
    onDirections: (ParkingLot) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origin.coordinates.toLatLng(), ZOOM)
    }
    var lastOriginKey by rememberSaveable {
        mutableStateOf("${origin.coordinates.latitude},${origin.coordinates.longitude},${origin.source}")
    }
    // Al buscar en una zona elegida el mapa ya está ahí.
    LaunchedEffect(origin) {
        val currentKey = "${origin.coordinates.latitude},${origin.coordinates.longitude},${origin.source}"
        if (currentKey != lastOriginKey) {
            lastOriginKey = currentKey
            if (origin.source != OriginSource.MAP_AREA) {
                camera.animate(CameraUpdateFactory.newLatLngZoom(origin.coordinates.toLatLng(), ZOOM))
            }
        }
    }
    val movedAway by remember(origin) {
        derivedStateOf {
            val target = camera.position.target
            !camera.isMoving &&
                distanceMeters(origin.coordinates, Coordinates(target.latitude, target.longitude)) > MOVE_THRESHOLD_METERS
        }
    }

    val density = LocalDensity.current.density
    val statusColors = SensorParkTheme.colors
    val icons = remember(density, statusColors) { MarkerIcons(density, statusColors) }
    val selected = remember(lots, selectedId) { lots.firstOrNull { it.parkingLot.id == selectedId } }

    Box(modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            // Sin permiso, isMyLocationEnabled lanza SecurityException.
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            ),
            contentPadding = PaddingValues(bottom = if (selected != null) 188.dp else 0.dp),
            onMapClick = { onSelect(null) },
        ) {
            for ((lot, _) in lots) {
                key(lot.id) {
                    val isSelected = lot.id == selectedId
                    Marker(
                        state = rememberMarkerState(key = lot.id, position = lot.location.toLatLng()),
                        icon = icons.icon(lot.freeSpots, lot.capacity, isSelected),
                        title = lot.name,
                        zIndex = if (isSelected) 1f else 0f,
                        anchor = Offset(0.5f, 0.5f),
                        onClick = {
                            onSelect(lot.id)
                            true // sin la ventana por defecto
                        },
                    )
                }
            }
        }

        if (movedAway) {
            ExtendedFloatingActionButton(
                onClick = { onSearchHere(camera.position.target.let { Coordinates(it.latitude, it.longitude) }) },
                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                text = { Text(stringResource(R.string.nearby_search_here)) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FloatingActionButton(
                onClick = onMyLocation,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.nearby_my_location))
            }

            if (lots.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = stringResource(R.string.nearby_empty),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            selected?.let { (lot, distance) ->
                Card(
                    onClick = { onOpenParkingLot(lot.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                            FavoriteButton(
                                favorite = lot.id in favoriteIds,
                                parkingLotName = lot.name,
                                onToggle = { onToggleFavorite(lot.id) },
                            )
                            AvailabilityBadge(lot.freeSpots, lot.capacity)
                        }
                        Text(
                            text = "${formatDistance(distance)} · ${lot.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onDirections(lot) },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(CoreR.string.directions_action),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                            Button(
                                onClick = { onOpenParkingLot(lot.id) },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.nearby_open_detail),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
