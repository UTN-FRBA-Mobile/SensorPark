package com.sensorpark.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.repository.ReservationRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Las reservas del usuario se leen de Firestore con un listener; todo lo que las cambia pasa por
 * las Cloud Functions de `functions/reservations.js`, que aplican las reglas y tienen la última
 * palabra. Los rechazos de negocio llegan con un `reason` y se devuelven como [ReservationOutcome.Rejected].
 */
class ReservationRepositoryFirebase @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : ReservationRepository {

    override fun myReservations(): Flow<DataResult<List<Reservation>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(DataResult.Failure(DataErrorReason.PERMISSION_DENIED))
            close()
            return@callbackFlow
        }
        // Las reglas sólo dejan leer las propias: la consulta tiene que filtrar por el usuario.
        val registration = db.collection(Collections.RESERVATIONS)
            .whereEqualTo(ReservationFields.USER_ID, uid)
            .orderBy(ReservationFields.ARRIVES_AT, Query.Direction.DESCENDING)
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

    override suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation> =
        call(
            ReservationFunctions.CREATE,
            buildMap {
                put("parkingLotId", request.parkingLotId)
                put("arrivesAt", request.arrivesAt.toString())
                put("durationMinutes", request.durationMinutes)
                request.spotType?.let { put("spotType", it.name) }
                request.spotId?.let { put("spotId", it) }
            },
        ) { confirmationFrom(it) }

    override suspend fun cancel(reservationId: String): ReservationOutcome<Unit> =
        call(ReservationFunctions.CANCEL, mapOf("reservationId" to reservationId)) { }

    override suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit> =
        call(
            ReservationFunctions.CHECK_IN,
            mapOf("reservationId" to reservationId, "latitude" to location.latitude, "longitude" to location.longitude),
        ) { }

    override suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary> =
        call(ReservationFunctions.CHECK_OUT, mapOf("reservationId" to reservationId)) { data ->
            CheckOutSummary(
                stayMinutes = (data["stayMinutes"] as? Number)?.toInt() ?: return@call null,
                amount = (data["amount"] as? Number)?.toInt() ?: return@call null,
            )
        }

    override suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition> =
        call(
            ReservationFunctions.JOIN_WAITLIST,
            buildMap {
                put("parkingLotId", request.parkingLotId)
                put("arrivesAt", request.arrivesAt.toString())
                put("durationMinutes", request.durationMinutes)
                request.spotType?.let { put("spotType", it.name) }
            },
        ) { data ->
            WaitlistPosition(
                entryId = data["entryId"] as? String ?: return@call null,
                position = (data["position"] as? Number)?.toInt() ?: return@call null,
            )
        }

    override suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit> =
        call(ReservationFunctions.LEAVE_WAITLIST, mapOf("entryId" to entryId)) { }

    /**
     * Llama a una function y traduce la respuesta. [parse] devuelve null si la respuesta no tiene la
     * forma esperada. Los errores con `reason` son rechazos de negocio; el resto, fallas.
     */
    private suspend fun <T> call(
        name: String,
        data: Map<String, Any>,
        parse: (Map<*, *>) -> T?,
    ): ReservationOutcome<T> = try {
        // La primera llamada después de un rato puede tardar unos segundos (arranque en frío).
        val result = withTimeout(TIMEOUT_MS) { functions.getHttpsCallable(name).call(data).await() }
        val response = result.getData() as? Map<*, *> ?: emptyMap<String, Any>()
        parse(response)?.let { ReservationOutcome.Success(it) } ?: ReservationOutcome.Failed(DataErrorReason.UNKNOWN)
    } catch (e: FirebaseFunctionsException) {
        val reason = ((e.details as? Map<*, *>)?.get("reason") as? String)?.let { ReservationRejection.from(it) }
        if (reason != null) ReservationOutcome.Rejected(reason) else ReservationOutcome.Failed(e.toReason())
    } catch (e: TimeoutCancellationException) {
        ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
    } catch (e: FirebaseNetworkException) {
        ReservationOutcome.Failed(DataErrorReason.NO_CONNECTION)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}

private fun FirebaseFunctionsException.toReason(): DataErrorReason = when (code) {
    Code.NOT_FOUND -> DataErrorReason.NOT_FOUND
    Code.PERMISSION_DENIED, Code.UNAUTHENTICATED -> DataErrorReason.PERMISSION_DENIED
    Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DataErrorReason.NO_CONNECTION
    else -> DataErrorReason.UNKNOWN
}
