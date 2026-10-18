package com.sensorpark.domain.repository

import com.sensorpark.domain.model.LocationReading

/** La ubicación del teléfono. La usan la búsqueda de estacionamientos y el check-in. */
interface LocationRepository {
    /**
     * Una lectura nueva y de alta precisión, para el check-in: ahí importa dónde está el conductor
     * ahora mismo. Devuelve null si falta el permiso, si la ubicación está apagada o si no llegó
     * ninguna lectura a tiempo.
     */
    suspend fun currentLocation(): LocationReading?

    /**
     * Una lectura aproximada y barata, para buscar estacionamientos cerca: alcanza con saber la
     * zona, así que usa precisión balanceada en vez de prender el GPS. Si no consigue una nueva,
     * devuelve la última conocida. Null si falta el permiso o no hay ninguna.
     */
    suspend fun approximateLocation(): LocationReading?

    fun hasLocationPermission(): Boolean
}
