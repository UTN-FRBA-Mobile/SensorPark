package com.sensorpark.domain.repository

/** Registro de este dispositivo para recibir notificaciones push. */
interface DeviceRepository {
    /** Asocia el token de push de este dispositivo al usuario con sesión iniciada. */
    suspend fun registerCurrentToken()

    /** Desasocia este dispositivo del usuario, antes de cerrar sesión: deja de recibir sus avisos. */
    suspend fun unregisterCurrentToken()
}
