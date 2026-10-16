package com.sensorpark.domain.repository

import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import kotlinx.coroutines.flow.Flow

/** Estacionamientos con sus contadores de lugares libres. Los flows emiten de nuevo con cada cambio. */
interface ParkingLotRepository {
    /** Todos los estacionamientos. Son pocos, así que se leen completos y se filtran en la app. */
    fun parkingLots(): Flow<DataResult<List<ParkingLot>>>

    /** Un estacionamiento con sus contadores. Sus lugares, uno por uno, no vienen acá. */
    fun parkingLot(id: String): Flow<DataResult<ParkingLot>>
}
