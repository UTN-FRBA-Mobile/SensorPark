package com.sensorpark.domain.reservation

import com.sensorpark.domain.geo.distanceMeters
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistEntry
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Las reglas de reserva, sin Android ni Firebase. Es la versión Kotlin de
 * `docs/reservation-rules.md`; la versión JavaScript, que tiene la última palabra, está en
 * `functions/reservationRules.js`. Si cambia una regla, cambian el documento y las dos versiones.
 * Todo lo que depende de la hora recibe `now`, para poder probarlo.
 */
object ReservationRules {
    val ALLOWED_DURATIONS_MINUTES = listOf(15, 30, 60, 120, 240, 480)
    val MAX_ADVANCE: Duration = Duration.ofDays(7)
    val PAST_GRACE: Duration = Duration.ofMinutes(5)
    const val MAX_ACTIVE_PER_USER = 2

    /** Una reserva que empieza dentro de este margen toma el lugar en el momento. */
    val NOW_WINDOW: Duration = Duration.ofMinutes(15)
    val EARLY_CHECK_IN: Duration = Duration.ofMinutes(15)
    val WAITLIST_OFFER_TIME: Duration = Duration.ofMinutes(5)
    val ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")

    fun endsAt(arrivesAt: Instant, durationMinutes: Int): Instant =
        arrivesAt.plus(Duration.ofMinutes(durationMinutes.toLong()))

    /**
     * Valida un pedido en el orden del documento y devuelve el primer motivo de rechazo, o null si
     * se puede intentar reservar. No mira la disponibilidad de lugares: eso lo hace [pickSpot].
     */
    fun validateRequest(
        request: ReservationRequest,
        lot: ParkingLot,
        category: Category,
        activeReservationsOfUser: Int,
        now: Instant,
    ): ReservationRejection? = validateSlot(request.arrivesAt, request.durationMinutes, lot, now)
        ?: when {
            request.spotType == SpotType.PRIORITY && category == Category.STANDARD ->
                ReservationRejection.CATEGORY_REQUIRED
            activeReservationsOfUser >= MAX_ACTIVE_PER_USER -> ReservationRejection.LIMIT_REACHED
            else -> null
        }

    /** Duración, llegada y horario: lo que se valida tanto al reservar como al anotarse en la lista de espera. */
    fun validateSlot(arrivesAt: Instant, durationMinutes: Int, lot: ParkingLot, now: Instant): ReservationRejection? =
        when {
            durationMinutes !in ALLOWED_DURATIONS_MINUTES -> ReservationRejection.INVALID_DURATION
            arrivesAt < now.minus(PAST_GRACE) -> ReservationRejection.IN_THE_PAST
            arrivesAt > now.plus(MAX_ADVANCE) -> ReservationRejection.TOO_FAR_AHEAD
            !fitsSchedule(lot, arrivesAt, endsAt(arrivesAt, durationMinutes)) -> ReservationRejection.OUTSIDE_SCHEDULE
            else -> null
        }

    /** La franja entera cae dentro del horario del estacionamiento, en la hora argentina y el mismo día. */
    fun fitsSchedule(lot: ParkingLot, arrivesAt: Instant, endsAt: Instant): Boolean {
        val schedule = lot.schedule ?: return true
        val start = arrivesAt.atZone(ZONE)
        val end = endsAt.atZone(ZONE)
        return start.toLocalDate() == end.toLocalDate() &&
            !start.toLocalTime().isBefore(schedule.opensAt) &&
            !end.toLocalTime().isAfter(schedule.closesAt)
    }

    /** Dos franjas `[inicio, fin)` se superponen; si una termina justo cuando empieza la otra, no. */
    fun overlaps(startA: Instant, endA: Instant, startB: Instant, endB: Instant): Boolean =
        startA < endB && startB < endA

    /**
     * Hasta cuándo una reserva activa ocupa su lugar. Una en uso que se pasó de hora sigue ahí: se
     * la cuenta hasta el fin de la ventana de "ahora", para que nadie reserve ese lugar ya.
     */
    fun takenUntil(reservation: Reservation, now: Instant): Instant =
        if (reservation.status == ReservationStatus.IN_USE) maxOf(reservation.endsAt, now.plus(NOW_WINDOW))
        else reservation.endsAt

    fun isInNowWindow(arrivesAt: Instant, now: Instant): Boolean = arrivesAt <= now.plus(NOW_WINDOW)

    /**
     * El lugar que le toca a un pedido, o null si no hay ninguno (`NO_SPOTS`). [lotReservations] son
     * las reservas del estacionamiento; sólo cuentan las activas.
     */
    fun pickSpot(
        request: ReservationRequest,
        spots: List<Spot>,
        lotReservations: List<Reservation>,
        category: Category,
        now: Instant,
    ): Spot? {
        val end = endsAt(request.arrivesAt, request.durationMinutes)
        val startsNow = isInNowWindow(request.arrivesAt, now)
        return spots
            .filter { spot ->
                (request.spotId == null || spot.id == request.spotId) &&
                    (request.spotType == null || spot.type == request.spotType) &&
                    (spot.type != SpotType.PRIORITY || spot.priorityCategory == category) &&
                    spot.status != SpotStatus.OUT_OF_SERVICE &&
                    (!startsNow || spot.status == SpotStatus.FREE) &&
                    lotReservations.none { r ->
                        r.spotId == spot.id && r.status.isActive &&
                            overlaps(request.arrivesAt, end, r.arrivesAt, takenUntil(r, now))
                    }
            }
            .minByOrNull { it.code }
    }

    /** Sólo se cancela antes del check-in. El dueño lo controla el backend. */
    fun cancelProblem(reservation: Reservation): ReservationRejection? =
        if (reservation.status == ReservationStatus.CONFIRMED) null else ReservationRejection.CANCEL_NOT_ALLOWED

    fun checkInProblem(
        reservation: Reservation,
        lot: ParkingLot,
        location: Coordinates,
        now: Instant,
    ): ReservationRejection? = when {
        reservation.status != ReservationStatus.CONFIRMED -> ReservationRejection.EXPIRED
        now < reservation.arrivesAt.minus(EARLY_CHECK_IN) -> ReservationRejection.TOO_EARLY
        now > reservation.toleranceUntil -> ReservationRejection.EXPIRED
        distanceMeters(location, lot.location) > lot.geofenceRadiusMeters -> ReservationRejection.TOO_FAR
        else -> null
    }

    fun stayMinutes(checkedInAt: Instant, checkedOutAt: Instant): Int =
        Duration.between(checkedInAt, checkedOutAt).toMinutes().toInt()

    /** Importe informativo: tarifa por hora × minutos reales / 60, redondeado. */
    fun amount(hourlyRate: Int, stayMinutes: Int): Int = (hourlyRate * stayMinutes / 60.0).roundToInt()

    /** Orden de la lista de espera: primero las categorías especiales, después por hora de pedido. */
    val waitlistOrder: Comparator<WaitlistEntry> =
        compareBy<WaitlistEntry> { if (it.category == Category.STANDARD) 1 else 0 }.thenBy { it.requestedAt }
}
