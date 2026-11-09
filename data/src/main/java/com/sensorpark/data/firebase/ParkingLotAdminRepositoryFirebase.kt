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
import com.sensorpark.domain.model.ParkingLevelDraft
import com.sensorpark.domain.model.ParkingLotDraft
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotPlanPosition
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

    override suspend fun saveLevel(parkingLotId: String, draft: ParkingLevelDraft): DataResult<String> = write {
        val levelsRef = db.collection(Collections.PARKING_LOTS).document(parkingLotId).collection(Collections.LEVELS)
        val existingDocs = levelsRef.get().await().documents
        val targetId = draft.id?.takeIf { it.isNotBlank() }
            ?: existingDocs.firstOrNull()?.id
            ?: SINGLE_LEVEL_ID
        val docRef = levelsRef.document(targetId)
        val fields = mapOf(
            LevelFields.NAME to draft.name.trim().ifBlank { DEFAULT_PLAN_NAME },
            LevelFields.ORDER to 1,
            LevelFields.IMAGE_BASE64 to draft.imageBase64,
            LevelFields.IMAGE_WIDTH to draft.imageWidth,
            LevelFields.IMAGE_HEIGHT to draft.imageHeight,
        )
        val batch = db.batch()
        batch.set(docRef, fields)
        // Garantiza un único plano por estacionamiento borrando cualquier piso extra previo.
        for (extra in existingDocs) {
            if (extra.id != targetId) {
                batch.delete(extra.reference)
            }
        }
        batch.commit().await()
        DataResult.Success(docRef.id)
    }

    override suspend fun deleteLevel(parkingLotId: String, levelId: String): DataResult<Unit> = write {
        val lotRef = db.collection(Collections.PARKING_LOTS).document(parkingLotId)
        val existingLevels = lotRef.collection(Collections.LEVELS).get().await()
        val allSpots = lotRef.collection(Collections.SPOTS).get().await()
        val batch = db.batch()
        for (levelDoc in existingLevels.documents) {
            batch.delete(levelDoc.reference)
        }
        for (doc in allSpots.documents) {
            if (doc.contains(SpotFields.LEVEL_ID) || doc.contains(SpotFields.PLAN_X)) {
                batch.update(
                    doc.reference,
                    mapOf(
                        SpotFields.LEVEL_ID to FieldValue.delete(),
                        SpotFields.PLAN_X to FieldValue.delete(),
                        SpotFields.PLAN_Y to FieldValue.delete(),
                    ),
                )
            }
        }
        batch.commit().await()
        DataResult.Success(Unit)
    }

    override suspend fun saveSpotPositions(
        parkingLotId: String,
        positions: Map<String, SpotPlanPosition?>,
    ): DataResult<Unit> = write {
        val spotsCollection = db.collection(Collections.PARKING_LOTS).document(parkingLotId).collection(Collections.SPOTS)
        // Sólo se actualizan lugares que existen realmente en el estacionamiento (consistencia 1 a 1).
        val existingIds = spotsCollection.get().await().documents.map { it.id }.toSet()
        val batch = db.batch()
        for ((spotId, pos) in positions) {
            if (spotId !in existingIds) continue
            val ref = spotsCollection.document(spotId)
            val fields = if (pos != null && pos.isValid) {
                mapOf(
                    SpotFields.LEVEL_ID to pos.levelId,
                    SpotFields.PLAN_X to pos.x.coerceIn(0f, 1f).toDouble(),
                    SpotFields.PLAN_Y to pos.y.coerceIn(0f, 1f).toDouble(),
                )
            } else {
                mapOf(
                    SpotFields.LEVEL_ID to FieldValue.delete(),
                    SpotFields.PLAN_X to FieldValue.delete(),
                    SpotFields.PLAN_Y to FieldValue.delete(),
                )
            }
            batch.update(ref, fields)
        }
        batch.commit().await()
        DataResult.Success(Unit)
    }

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
            else -> ReservationOutcome.Failed(e.toReason())
        }
    } catch (e: TimeoutCancellationException) {
        ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
    } catch (e: FirebaseNetworkException) {
        ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
    }

    private companion object {
        const val SPOT_SERVICE = "setSpotService"
        const val DELETE_SPOT = "deleteSpot"
        const val SINGLE_LEVEL_ID = "main"
        const val DEFAULT_PLAN_NAME = "Plano"
        const val TIMEOUT_MS = 20_000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
