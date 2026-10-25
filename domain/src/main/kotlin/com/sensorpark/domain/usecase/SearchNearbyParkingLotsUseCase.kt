package com.sensorpark.domain.usecase

import com.sensorpark.domain.geo.distanceMeters
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.map
import com.sensorpark.domain.repository.ParkingLotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Filtros de la búsqueda. Con [spotType], sólo los estacionamientos que tienen lugares de ese
 * tipo; con [onlyWithFreeSpots], sólo los que tienen alguno libre (del tipo pedido, si hay uno);
 * con [favoriteIds], sólo esos, estén a la distancia que estén (T19).
 */
data class ParkingLotFilter(
    val spotType: SpotType? = null,
    val onlyWithFreeSpots: Boolean = false,
    val favoriteIds: Set<String>? = null,
) {
    fun matches(lot: ParkingLot): Boolean = (favoriteIds == null || lot.id in favoriteIds) && matchesSpots(lot)

    private fun matchesSpots(lot: ParkingLot): Boolean = when {
        spotType == null -> !onlyWithFreeSpots || lot.freeSpots > 0
        onlyWithFreeSpots -> lot.freeSpotsOf(spotType) > 0
        else -> lot.capacityOf(spotType) > 0
    }
}

/**
 * Estacionamientos a menos de [radiusMeters] de un punto, del más cercano al más lejano.
 * Firestore no consulta por distancia: se leen todos y se calcula acá. Si un día son miles,
 * habría que guardar un geohash en cada documento y consultar por rango.
 */
class SearchNearbyParkingLotsUseCase @Inject constructor(
    private val repository: ParkingLotRepository,
) {
    operator fun invoke(
        origin: Coordinates,
        radiusMeters: Double = DEFAULT_RADIUS_METERS,
        filter: ParkingLotFilter = ParkingLotFilter(),
    ): Flow<DataResult<List<NearbyParkingLot>>> =
        repository.parkingLots().map { result ->
            result.map { lots -> nearby(lots, origin, radiusMeters, filter) }
        }

    companion object {
        const val DEFAULT_RADIUS_METERS = 2_000.0

        fun nearby(
            lots: List<ParkingLot>,
            origin: Coordinates,
            radiusMeters: Double,
            filter: ParkingLotFilter,
        ): List<NearbyParkingLot> =
            lots.asSequence()
                .filter(filter::matches)
                .map { NearbyParkingLot(it, distanceMeters(origin, it.location)) }
                // Los favoritos se muestran aunque queden lejos: son los que el usuario eligió.
                .filter { filter.favoriteIds != null || it.distanceMeters <= radiusMeters }
                .sortedBy { it.distanceMeters }
                .toList()
    }
}
