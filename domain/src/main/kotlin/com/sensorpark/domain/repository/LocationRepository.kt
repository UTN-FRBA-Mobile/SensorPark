package com.sensorpark.domain.repository

import com.sensorpark.domain.model.LocationReading

/** La ubicación del teléfono. La usa el check-in para probar que el conductor llegó. */
interface LocationRepository {
    /**
     * Una lectura nueva y de alta precisión. Devuelve null si falta el permiso, si la ubicación
     * está apagada o si no llegó ninguna lectura a tiempo.
     */
    suspend fun currentLocation(): LocationReading?

    fun hasLocationPermission(): Boolean

    /**
     * Una lectura de precisión balanceada, suficiente para buscar cercanos y barata en batería.
     * Anda con ubicación aproximada. Devuelve null si falta el permiso o no llegó a tiempo.
     */
    suspend fun approximateLocation(): LocationReading?

    /** Hay permiso de ubicación, precisa o aproximada. */
    fun hasApproximatePermission(): Boolean
}
