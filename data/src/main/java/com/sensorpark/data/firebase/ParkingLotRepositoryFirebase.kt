package com.sensorpark.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.repository.ParkingLotRepository
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Lee los estacionamientos con listeners: los contadores de lugares libres llegan actualizados
 * sin volver a pedirlos. Sin conexión, Firestore entrega lo último que tenga en caché.
 */
class ParkingLotRepositoryFirebase @Inject constructor(
    private val db: FirebaseFirestore,
) : ParkingLotRepository {

    override fun parkingLots(): Flow<DataResult<List<ParkingLot>>> = callbackFlow {
        val registration = db.collection(Collections.PARKING_LOTS).addSnapshotListener { snapshot, error ->
            when {
                error != null -> fail(error)
                snapshot == null -> Unit
                // Vacío y desde la caché: todavía no se pudo hablar con el servidor.
                snapshot.isEmpty && snapshot.metadata.isFromCache ->
                    trySend(DataResult.Failure(DataErrorReason.NO_CONNECTION))
                else -> trySend(
                    DataResult.Success(snapshot.documents.mapNotNull { parkingLotFrom(it.id, it.data.orEmpty()) }),
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override fun parkingLot(id: String): Flow<DataResult<ParkingLot>> = callbackFlow {
        val registration = db.collection(Collections.PARKING_LOTS).document(id).addSnapshotListener { doc, error ->
            when {
                error != null -> fail(error)
                doc == null -> Unit
                !doc.exists() -> trySend(
                    DataResult.Failure(
                        if (doc.metadata.isFromCache) DataErrorReason.NO_CONNECTION else DataErrorReason.NOT_FOUND,
                    ),
                )
                else -> trySend(
                    parkingLotFrom(doc.id, doc.data.orEmpty())?.let { DataResult.Success(it) }
                        ?: DataResult.Failure(DataErrorReason.NOT_FOUND),
                )
            }
        }
        awaitClose { registration.remove() }
    }

    /** Después de un error el listener no emite más: se avisa y se cierra el flow. */
    private fun ProducerScope<DataResult<Nothing>>.fail(error: FirebaseFirestoreException) {
        trySend(DataResult.Failure(error.toReason()))
        close()
    }
}

internal fun FirebaseFirestoreException.toReason(): DataErrorReason = when (code) {
    Code.NOT_FOUND -> DataErrorReason.NOT_FOUND
    Code.PERMISSION_DENIED, Code.UNAUTHENTICATED -> DataErrorReason.PERMISSION_DENIED
    Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DataErrorReason.NO_CONNECTION
    else -> DataErrorReason.UNKNOWN
}
