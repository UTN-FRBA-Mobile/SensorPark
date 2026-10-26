package com.sensorpark.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.sensorpark.core.ui.components.ParkingLotCard
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
    // Al buscar en una zona elegida el mapa ya está ahí.
    LaunchedEffect(origin) {
        if (origin.source != OriginSource.MAP_AREA) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(origin.coordinates.toLatLng(), ZOOM))
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
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
        SmallFloatingActionButton(
            onClick = onMyLocation,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.nearby_my_location))
        }

        if (lots.isEmpty()) {
            Text(
                text = stringResource(R.string.nearby_empty),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
        selected?.let { (lot, distance) ->
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParkingLotCard(
                    name = lot.name,
                    subtitle = "${formatDistance(distance)} · ${lot.address}",
                    freeSpots = lot.freeSpots,
                    capacity = lot.capacity,
                    onClick = { onOpenParkingLot(lot.id) },
                    favorite = lot.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(lot.id) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDirections(lot) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(CoreR.string.directions_action))
                    }
                    Button(onClick = { onOpenParkingLot(lot.id) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nearby_open_detail))
                    }
                }
            }
        }
    }
}
