package com.sensorpark.domain.repository

import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.SpotReassignment
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.model.WaitlistEntry
import kotlinx.coroutines.flow.Flow

/**
 * Reservas del usuario con sesión. Leer es directo de la base y en vivo; todo lo que cambia una
 * reserva pasa por el backend, que valida las reglas y tiene la última palabra.
 */
interface ReservationRepository {
    /** Las reservas del usuario, de la más reciente a la más vieja. */
    fun myReservations(): Flow<DataResult<List<Reservation>>>

    suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation>

    suspend fun cancel(reservationId: String): ReservationOutcome<Unit>

    /** Pide otro lugar del mismo tipo porque el asignado apareció ocupado por otro auto. */
    suspend fun reassignSpot(reservationId: String): ReservationOutcome<SpotReassignment>

    suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit>

    suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary>

    /** Anotarse en la lista de espera con la misma franja y tipo de lugar del pedido. */
    suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition>

    suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit>

    /** Las entradas del usuario en listas de espera, de la más reciente a la más vieja. */
    fun myWaitlist(): Flow<DataResult<List<WaitlistEntry>>>

    /** Quedarse con el lugar ofrecido: lo convierte en una reserva confirmada. */
    suspend fun acceptWaitlistOffer(entryId: String): ReservationOutcome<ReservationConfirmation>

    /** No lo quiere: el lugar pasa al siguiente de la fila. */
    suspend fun declineWaitlistOffer(entryId: String): ReservationOutcome<Unit>
}
