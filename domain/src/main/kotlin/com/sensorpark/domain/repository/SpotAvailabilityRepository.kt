package com.sensorpark.domain.repository

import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.SpotAvailability
import kotlinx.coroutines.flow.Flow

/** Los lugares de un estacionamiento en vivo. El flow emite de nuevo con cada cambio. */
interface SpotAvailabilityRepository {
    /**
     * Los lugares del estacionamiento [parkingLotId], ordenados por código. Escucha sólo ese
     * estacionamiento y deja de escuchar cuando el flow se cancela, así que hay que recolectarlo
     * sólo mientras la pantalla esté visible. Si falla, emite un [DataResult.Failure] y termina.
     */
    fun observeSpots(parkingLotId: String): Flow<DataResult<SpotAvailability>>
}
