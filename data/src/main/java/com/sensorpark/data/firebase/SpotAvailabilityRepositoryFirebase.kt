package com.sensorpark.data.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Escucha los lugares de un estacionamiento con un listener de Firestore, que avisa cada vez que
 * un lugar cambia (lo escribe el simulador, una reserva o su encargado). Sin conexión, Firestore
 * entrega la última copia que tenga y [SpotAvailability.isFromCache] lo indica; al volver la red
 * reconecta solo y emite de nuevo, ya como datos del servidor.
 */
class SpotAvailabilityRepositoryFirebase @Inject constructor(
    private val db: FirebaseFirestore,
) : SpotAvailabilityRepository {

    override fun observeSpots(parkingLotId: String): Flow<DataResult<SpotAvailability>> =
        events(parkingLotId)
            // La conversión de documentos a modelos no corre en el hilo principal.
            .map { it.toResult() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()

    private fun events(parkingLotId: String): Flow<Event> = callbackFlow {
        // INCLUDE avisa también cuando sólo cambia el origen de los datos (caché o servidor), que es
        // lo que permite pasar de "Sin conexión" a "En vivo" sin que haya cambiado ningún lugar.
        val registration = db.collection(Collections.PARKING_LOTS).document(parkingLotId)
            .collection(Collections.SPOTS)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    // Después de un error el listener no emite más: se avisa y se cierra el flow.
                    error != null -> {
                        trySend(Event.Failed(error.toReason()))
                        close()
                    }
                    snapshot == null -> Unit
                    else -> trySend(Event.Snapshot(snapshot.documents, snapshot.metadata.isFromCache))
                }
            }
        Log.d(TAG, "Listener de lugares abierto: $parkingLotId")
        awaitClose {
            registration.remove()
            Log.d(TAG, "Listener de lugares cerrado: $parkingLotId")
        }
    }

    private sealed interface Event {
        class Snapshot(val documents: List<DocumentSnapshot>, val isFromCache: Boolean) : Event
        class Failed(val reason: DataErrorReason) : Event
    }

    private fun Event.toResult(): DataResult<SpotAvailability> = when (this) {
        is Event.Failed -> DataResult.Failure(reason)
        is Event.Snapshot ->
            // Vacío y desde la caché: todavía no se pudo hablar con el servidor.
            if (documents.isEmpty() && isFromCache) {
                DataResult.Failure(DataErrorReason.NO_CONNECTION)
            } else {
                DataResult.Success(spotAvailabilityFrom(documents.map { it.toSpotDocument() }, isFromCache))
            }
    }

    private fun DocumentSnapshot.toSpotDocument() = SpotDocument(
        id = id,
        data = data.orEmpty(),
        // ESTIMATE: un cambio local todavía sin confirmar trae una hora estimada, no null.
        updatedAt = getTimestamp(SpotFields.UPDATED_AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant(),
    )

    private companion object {
        const val TAG = "SpotAvailability"
    }
}
