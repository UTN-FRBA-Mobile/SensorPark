package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.NotificationRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReminderScheduler
import com.sensorpark.domain.repository.ReservationRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * Pone al día todo lo que el teléfono hace por su cuenta a partir de las reservas: el aviso de
 * confirmación, el recordatorio previo, la geocerca de llegada y el aviso persistente de la
 * estadía. También limpia lo que sobra.
 *
 * Es una reconciliación y no una reacción a un evento, porque una reserva puede cancelarse o
 * vencer desde el backend sin que el teléfono se entere en el momento. Correrlo al abrir la app
 * y con cada cambio de las reservas deja todo consistente, y hace que T20 funcione sin depender
 * de la pantalla de reservas (T15).
 *
 * La fila guardada de la geocerca es además la marca de "esta reserva ya la vi": existe aunque el
 * registro en el sistema falle por falta de permiso, así el aviso de confirmación no se repite.
 */
class SyncReservationAlertsUseCase @Inject constructor(
    private val reservations: ReservationRepository,
    private val parkingLots: ParkingLotRepository,
    private val geofences: GeofenceRepository,
    private val reminders: ReminderScheduler,
    private val notifications: NotificationRepository,
    private val messages: ReservationAlertMessages,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val mine = (reservations.myReservations().first() as? DataResult.Success)?.value ?: return
        val lots = (parkingLots.parkingLots().first() as? DataResult.Success)?.value.orEmpty()
        sync(mine, lots.associateBy { it.id })
    }

    /** Separado del acceso a los repositorios para poder probarlo con listas fijas. */
    suspend fun sync(mine: List<Reservation>, lots: Map<String, ParkingLot>) {
        val now = clock.instant()
        val tracked = geofences.active().associateBy { it.reservationId }

        mine.forEach { reservation ->
            when (reservation.status) {
                ReservationStatus.CONFIRMED -> {
                    val lot = lots[reservation.parkingLotId]
                    if (lot != null && reservation.toleranceUntil > now && reservation.id !in tracked) {
                        announce(reservation, lot)
                    }
                }
                ReservationStatus.IN_USE -> {
                    // Ya llegó: la geocerca, el recordatorio y los avisos previos no tienen más sentido.
                    forget(reservation.id)
                    clearPreArrivalNotices(reservation.id)
                    notifications.notify(
                        messages.stay(reservation.id, reservation.spotCode, reservation.endsAt),
                    )
                }
                ReservationStatus.FINISHED, ReservationStatus.CANCELLED, ReservationStatus.EXPIRED -> {
                    forget(reservation.id)
                    clearPreArrivalNotices(reservation.id)
                    notifications.cancel(messages.stayKey(reservation.id))
                }
            }
        }

        // Geocercas de reservas que ya no existen o que vencieron sin que nadie las limpiara.
        val mineById = mine.associateBy { it.id }
        tracked.values
            .filter { it.reservationId !in mineById || it.expiresAt <= now }
            .forEach { forget(it.reservationId) }
    }

    /** Primera vez que este teléfono ve la reserva: avisa, programa y registra la geocerca. */
    private suspend fun announce(reservation: Reservation, lot: ParkingLot) {
        notifications.notify(
            messages.reservationConfirmed(reservation.id, lot.name, reservation.spotCode, reservation.arrivesAt),
        )
        val notifyAt = reservation.arrivesAt.minus(REMINDER_BEFORE)
        reminders.scheduleArrivalReminder(reservation.id, notifyAt)
        geofences.register(
            ActiveGeofence(
                reservationId = reservation.id,
                parkingLotName = lot.name,
                spotCode = reservation.spotCode,
                center = lot.location,
                radiusMeters = lot.geofenceRadiusMeters,
                expiresAt = reservation.toleranceUntil,
                arrivesAt = reservation.arrivesAt,
            ),
        )
    }

    private suspend fun forget(reservationId: String) {
        geofences.remove(reservationId)
        reminders.cancel(reservationId)
    }

    /** Confirmación, recordatorio y llegada: una vez que llegó, o que la reserva terminó, sobran. */
    private suspend fun clearPreArrivalNotices(reservationId: String) {
        messages.preArrivalKeys(reservationId).forEach { notifications.cancel(it) }
    }

    companion object {
        /** Cuánto antes de la hora de llegada avisa el recordatorio. */
        val REMINDER_BEFORE: Duration = Duration.ofMinutes(30)
    }
}
