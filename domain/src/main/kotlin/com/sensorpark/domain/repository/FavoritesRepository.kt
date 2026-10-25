package com.sensorpark.domain.repository

import com.sensorpark.domain.model.Stay
import kotlinx.coroutines.flow.Flow

/**
 * Los estacionamientos favoritos del usuario (T19). Viven en el teléfono, así que se ven y se
 * cambian sin conexión; los cambios suben al backend en segundo plano cuando vuelve la red.
 */
interface FavoritesRepository {
    /** Los ids de los favoritos, en vivo. */
    fun favorites(): Flow<Set<String>>

    /** Marca o desmarca al instante; la sincronización con el backend queda pendiente. */
    suspend fun setFavorite(parkingLotId: String, favorite: Boolean)

    /** Sube los cambios pendientes. Devuelve false si no pudo con alguno y hay que reintentar. */
    suspend fun syncPending(): Boolean
}

/**
 * El historial de estadías terminadas, guardado en el teléfono para verlo sin conexión (T19). Se
 * pone al día solo con las reservas del usuario mientras alguien lo mira.
 */
interface StayHistoryRepository {
    /** De la más reciente a la más vieja. */
    fun stays(): Flow<List<Stay>>
}
