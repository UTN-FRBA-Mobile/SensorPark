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
import com.sensorpark.domain.model.WaitlistPosition
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
