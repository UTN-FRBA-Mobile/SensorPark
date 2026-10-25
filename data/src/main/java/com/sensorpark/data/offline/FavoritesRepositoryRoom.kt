package com.sensorpark.data.offline

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sensorpark.data.firebase.Collections
import com.sensorpark.data.firebase.FavoriteFields
import com.sensorpark.data.local.FavoriteDao
import com.sensorpark.data.local.FavoriteEntity
import com.sensorpark.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Dónde viven los favoritos en el backend: `users/{uid}/favorites/{parkingLotId}`. */
interface FavoritesRemote {
    /** Los del backend, o null si no hay sesión o no se pudo leer (por ejemplo, sin red). */
    suspend fun fetch(): Set<String>?

    suspend fun add(parkingLotId: String)

    suspend fun remove(parkingLotId: String)
}

/** Programa la subida de los cambios pendientes para cuando haya red. */
interface FavoritesSyncScheduler {
    fun schedule()
}

/**
 * Los favoritos con Room como fuente de verdad en el teléfono (T19): marcar o desmarcar escribe
 * acá al instante, con o sin red, y deja el cambio pendiente; un Worker lo sube cuando hay red.
 * Al empezar a mirarlos trae los del backend, así reinstalar la app los recupera.
 */
@Singleton
class FavoritesRepositoryRoom @Inject constructor(
    private val dao: FavoriteDao,
    private val remote: FavoritesRemote,
    private val scheduler: FavoritesSyncScheduler,
    private val clock: Clock,
) : FavoritesRepository {

    override fun favorites(): Flow<Set<String>> =
        dao.observeIds().map { it.toSet() }.onStart { refreshFromRemote() }

    override suspend fun setFavorite(parkingLotId: String, favorite: Boolean) {
        val current = dao.find(parkingLotId)
        when {
            favorite -> dao.save(
                FavoriteEntity(
                    parkingLotId = parkingLotId,
                    addedAtEpochMillis = current?.addedAtEpochMillis ?: clock.millis(),
                    pendingSync = true,
                ),
            )
            current != null -> dao.save(current.copy(pendingSync = true, removed = true))
            else -> return
        }
        scheduler.schedule()
    }

    override suspend fun syncPending(): Boolean {
        var allDone = true
        for (favorite in dao.pending()) {
            try {
                if (favorite.removed) {
                    remote.remove(favorite.parkingLotId)
                    dao.delete(favorite.parkingLotId)
                } else {
                    remote.add(favorite.parkingLotId)
                    dao.save(favorite.copy(pendingSync = false))
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo subir el favorito ${favorite.parkingLotId}", e)
                allDone = false
            }
        }
        return allDone
    }

    /**
     * Reemplaza los ya sincronizados por los del backend. Los cambios pendientes del teléfono no se
     * tocan: son más nuevos que lo que tiene el backend.
     */
    private suspend fun refreshFromRemote() {
        val ids = remote.fetch() ?: return
        val pending = dao.pending().map { it.parkingLotId }.toSet()
        dao.deleteSynced()
        ids.filterNot { it in pending }.forEach {
            dao.save(FavoriteEntity(parkingLotId = it, addedAtEpochMillis = clock.millis(), pendingSync = false))
        }
    }

    private companion object {
        const val TAG = "SensorParkFavorites"
    }
}

/** Los favoritos en Firestore. Las reglas sólo dejan leerlos y escribirlos a su dueño. */
class FavoritesRemoteFirestore @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : FavoritesRemote {

    override suspend fun fetch(): Set<String>? {
        val uid = auth.currentUser?.uid ?: return null
        return runCatching {
            collection(uid).get().await().documents.map { it.id }.toSet()
        }.getOrNull()
    }

    override suspend fun add(parkingLotId: String) {
        val uid = auth.currentUser?.uid ?: error("Sin sesión")
        collection(uid).document(parkingLotId).set(
            mapOf(
                FavoriteFields.PARKING_LOT_ID to parkingLotId,
                FavoriteFields.ADDED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun remove(parkingLotId: String) {
        val uid = auth.currentUser?.uid ?: error("Sin sesión")
        collection(uid).document(parkingLotId).delete().await()
    }

    private fun collection(uid: String) =
        db.collection(Collections.USERS).document(uid).collection(Collections.FAVORITES)
}
