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
}
