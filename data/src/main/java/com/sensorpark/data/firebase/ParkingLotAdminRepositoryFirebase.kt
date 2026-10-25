package com.sensorpark.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLotDraft
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.repository.ParkingLotAdminRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject

/**
 * El panel del administrador contra Firebase (T22). Los datos y los lugares se escriben directo en
 * Firestore, donde firestore.rules deja hacerlo sólo a quien figura en `adminUids` (y no deja tocar
 * los contadores). Sacar de servicio o borrar un lugar pasa por las functions de `functions/admin.js`.
 */
class ParkingLotAdminRepositoryFirebase @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : ParkingLotAdminRepository {

    override suspend fun create(draft: ParkingLotDraft): DataResult<String> = write {
        val uid = auth.currentUser?.uid ?: return@write DataResult.Failure(DataErrorReason.PERMISSION_DENIED)
        val ref = db.collection(Collections.PARKING_LOTS).document()
        // Los contadores no van: los calcula updateFreeSpotCounters cuando se agregan lugares.
        ref.set(fieldsOf(draft).filterValues { it !is FieldValue } + (ParkingLotFields.ADMIN_UIDS to listOf(uid))).await()
        DataResult.Success(ref.id)
    }

    override suspend fun update(parkingLotId: String, draft: ParkingLotDraft): DataResult<Unit> = write {
        db.collection(Collections.PARKING_LOTS).document(parkingLotId).update(fieldsOf(draft)).await()
        DataResult.Success(Unit)
    }

    override suspend fun saveSpot(parkingLotId: String, spot: SpotDraft): DataResult<Unit> = write {
        val code = spot.code.trim()
        val ref = db.collection(Collections.PARKING_LOTS).document(parkingLotId).collection(Collections.SPOTS).document(code)
        val fields = mapOf(
            SpotFields.CODE to code,
            SpotFields.TYPE to spot.type.name,
            SpotFields.PRIORITY_CATEGORY to
                if (spot.type == SpotType.PRIORITY) spot.priorityCategory?.name else FieldValue.delete(),
        )
        if (ref.get().await().exists()) {
            ref.update(fields).await()
        } else {
            // Uno nuevo arranca libre. `set` no acepta FieldValue.delete(): se saca antes.
            ref.set(
                fields.filterValues { it != null && it !is FieldValue } + mapOf(
                    SpotFields.STATUS to SpotStatus.FREE.name,
                    SpotFields.UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        DataResult.Success(Unit)
    }

    override suspend fun setSpotInService(
        parkingLotId: String,
        spotId: String,
        inService: Boolean,
    ): ReservationOutcome<Unit> =
        call(SPOT_SERVICE, mapOf("parkingLotId" to parkingLotId, "spotId" to spotId, "inService" to inService))

    override suspend fun deleteSpot(parkingLotId: String, spotId: String): ReservationOutcome<Unit> =
        call(DELETE_SPOT, mapOf("parkingLotId" to parkingLotId, "spotId" to spotId))

    override fun reservationsBetween(
        parkingLotId: String,
        from: Instant,
        until: Instant,
    ): Flow<DataResult<List<Reservation>>> = callbackFlow {
        // Las reglas dejan leerlas al administrador de ese estacionamiento: la consulta filtra por él.
        val registration = db.collection(Collections.RESERVATIONS)
            .whereEqualTo(ReservationFields.PARKING_LOT_ID, parkingLotId)
            .whereGreaterThanOrEqualTo(ReservationFields.ARRIVES_AT, Timestamp(Date.from(from)))
            .whereLessThan(ReservationFields.ARRIVES_AT, Timestamp(Date.from(until)))
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        trySend(DataResult.Failure(error.toReason()))
                        close()
                    }
                    snapshot != null -> trySend(
                        DataResult.Success(snapshot.documents.mapNotNull { reservationFrom(it.id, it.data.orEmpty()) }),
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /** Los campos que edita el administrador. Sin horario, se borra el que había. */
    private fun fieldsOf(draft: ParkingLotDraft): Map<String, Any> = buildMap {
        put(ParkingLotFields.NAME, draft.name.trim())
        put(ParkingLotFields.ADDRESS, draft.address.trim())
        draft.location?.let { put(ParkingLotFields.LOCATION, GeoPoint(it.latitude, it.longitude)) }
        val schedule = draft.schedule
        put(
            ParkingLotFields.SCHEDULE,
            if (schedule == null) {
                FieldValue.delete()
            } else {
                mapOf(
                    ParkingLotFields.OPENS_AT to TIME.format(schedule.opensAt),
                    ParkingLotFields.CLOSES_AT to TIME.format(schedule.closesAt),
                )
            },
        )
        put(ParkingLotFields.TOLERANCE_MINUTES, draft.toleranceMinutes)
        put(ParkingLotFields.GEOFENCE_RADIUS_METERS, draft.geofenceRadiusMeters)
        put(ParkingLotFields.HOURLY_RATE, draft.hourlyRate)
    }

    private suspend fun <T> write(block: suspend () -> DataResult<T>): DataResult<T> = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: FirebaseFirestoreException) {
        DataResult.Failure(e.toReason())
    } catch (e: TimeoutCancellationException) {
        // Sin red, Firestore guarda la escritura y la manda después; acá se avisa que no se confirmó.
        DataResult.Failure(DataErrorReason.NO_CONNECTION)
    }

    private suspend fun call(name: String, data: Map<String, Any>): ReservationOutcome<Unit> = try {
        withTimeout(TIMEOUT_MS) { functions.getHttpsCallable(name).call(data).await() }
        ReservationOutcome.Success(Unit)
    } catch (e: FirebaseFunctionsException) {
        val reason = ((e.details as? Map<*, *>)?.get("reason") as? String)?.let { ReservationRejection.from(it) }
        when {
            reason != null -> ReservationOutcome.Rejected(reason)
            e.code == FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                ReservationOutcome.Failed(DataErrorReason.PERMISSION_DENIED)
            e.code == FirebaseFunctionsException.Code.NOT_FOUND -> ReservationOutcome.Failed(DataErrorReason.NOT_FOUND)
            e.code == FirebaseFunctionsException.Code.UNAVAILABLE -> ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
            else -> ReservationOutcome.Failed(DataErrorReason.UNKNOWN)
        }
    } catch (e: TimeoutCancellationException) {
        ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
    } catch (e: FirebaseNetworkException) {
        ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
    }

    private companion object {
        const val SPOT_SERVICE = "setSpotService"
        const val DELETE_SPOT = "deleteSpot"
        const val TIMEOUT_MS = 20_000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
