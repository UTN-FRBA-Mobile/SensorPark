package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotReassignment
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.reservation.ReservationRules
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

// Casos de uso de reservas. Antes de llamar al backend validan lo que la app puede saber sola
// (duración, horario, categoría, límite), para responder rápido y sin red. La disponibilidad de
// lugares la decide sólo el backend: la app no ve las reservas de los demás.

class CreateReservationUseCase @Inject constructor(
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        request: ReservationRequest,
        lot: ParkingLot,
        category: Category,
    ): ReservationOutcome<ReservationConfirmation> {
        val active = (reservations.myReservations().first() as? DataResult.Success)
            ?.value?.count { it.status.isActive } ?: 0
        ReservationRules.validateRequest(request, lot, category, active, clock.instant())
            ?.let { return ReservationOutcome.Rejected(it) }
        return reservations.create(request)
    }
}

class CancelReservationUseCase @Inject constructor(
    private val reservations: ReservationRepository,
) {
    suspend operator fun invoke(reservation: Reservation): ReservationOutcome<Unit> {
        ReservationRules.cancelProblem(reservation)?.let { return ReservationOutcome.Rejected(it) }
        return reservations.cancel(reservation.id)
    }
}

class CheckInUseCase @Inject constructor(
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        reservation: Reservation,
        lot: ParkingLot,
        location: Coordinates,
    ): ReservationOutcome<Unit> {
        ReservationRules.checkInProblem(reservation, lot, location, clock.instant())
            ?.let { return ReservationOutcome.Rejected(it) }
        return reservations.checkIn(reservation.id, location)
    }
}

/**
 * Pedir otro lugar porque el asignado apareció ocupado por otro auto. La franja no cambia: sólo
 * cambia a qué lugar apunta la reserva.
 */
class ReassignSpotUseCase @Inject constructor(
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(reservation: Reservation, spot: Spot?): ReservationOutcome<SpotReassignment> {
        ReservationRules.reassignProblem(reservation, spot, clock.instant())
            ?.let { return ReservationOutcome.Rejected(it) }
        return reservations.reassignSpot(reservation.id)
    }
}

/**
 * Check-out: termina la estadía y libera el lugar. Puede ser antes o después del fin de la franja;
 * lo único que no se puede es hacerlo sin estar en uso. El importe lo calcula el backend.
 */
class CheckOutUseCase @Inject constructor(
    private val reservations: ReservationRepository,
) {
    suspend operator fun invoke(reservation: Reservation): ReservationOutcome<CheckOutSummary> {
        if (reservation.status != ReservationStatus.IN_USE) {
            return ReservationOutcome.Rejected(ReservationRejection.INVALID_STATUS)
        }
        return reservations.checkOut(reservation.id)
    }
}

class JoinWaitlistUseCase @Inject constructor(
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        request: ReservationRequest,
        lot: ParkingLot,
        category: Category,
    ): ReservationOutcome<WaitlistPosition> {
        // Como al reservar, salvo el límite: anotarse no toma ningún lugar.
        ReservationRules.validateRequest(request, lot, category, activeReservationsOfUser = 0, now = clock.instant())
            ?.let { return ReservationOutcome.Rejected(it) }
        return reservations.joinWaitlist(request)
    }
}

/**
 * Quedarse con el lugar que ofrece la lista de espera. Como al reservar, cuenta el límite de
 * reservas activas: la oferta no lo saltea.
 */
class AcceptWaitlistOfferUseCase @Inject constructor(
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(entry: WaitlistEntry): ReservationOutcome<ReservationConfirmation> {
        ReservationRules.offerProblem(entry, clock.instant())?.let { return ReservationOutcome.Rejected(it) }
        val active = (reservations.myReservations().first() as? DataResult.Success)
            ?.value?.count { it.status.isActive } ?: 0
        if (active >= ReservationRules.MAX_ACTIVE_PER_USER) {
            return ReservationOutcome.Rejected(ReservationRejection.LIMIT_REACHED)
        }
        return reservations.acceptWaitlistOffer(entry.id)
    }
}

class DeclineWaitlistOfferUseCase @Inject constructor(
    private val reservations: ReservationRepository,
) {
    suspend operator fun invoke(entry: WaitlistEntry): ReservationOutcome<Unit> {
        // Una oferta vencida igual se puede rechazar: el backend la da por perdida y sigue la fila.
        if (entry.status != WaitlistStatus.OFFERED) return ReservationOutcome.Rejected(ReservationRejection.NO_OFFER)
        return reservations.declineWaitlistOffer(entry.id)
    }
}
