package com.sensorpark.domain.repository

import com.sensorpark.domain.model.ActiveGeofence

/**
 * Las geocercas de las reservas: el sistema operativo avisa cuando el usuario entra al círculo,
 * sin tener el GPS prendido todo el tiempo. La implementación las registra en Play Services y
 * guarda una copia en el teléfono, porque un reinicio las borra.
 */
interface GeofenceRepository {
    /**
     * Registra la geocerca de una reserva, reemplazando la anterior si ya había una.
     * @return false si falta el permiso de ubicación en segundo plano: la app sigue andando y el
     * usuario hace el check-in a mano desde "Mis reservas".
     */
    suspend fun register(geofence: ActiveGeofence): Boolean

    suspend fun remove(reservationId: String)

    suspend fun active(): List<ActiveGeofence>

    suspend fun find(reservationId: String): ActiveGeofence?

    /** Vuelve a registrar en el sistema todas las guardadas; se llama al reiniciar el teléfono. */
    suspend fun restoreAll()

    fun hasBackgroundLocationPermission(): Boolean
}
