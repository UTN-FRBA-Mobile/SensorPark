package com.sensorpark.domain.model

import java.time.Instant

enum class ReservationStatus {
    /** Reservada, todavía sin check-in. */
    CONFIRMED,

    /** Hizo check-in: el auto está en el lugar. */
    IN_USE,
    FINISHED,
    CANCELLED,

    /** No hizo check-in dentro de la tolerancia. */
    EXPIRED;

    /** Las activas son las que toman un lugar y cuentan para el límite por usuario. */
    val isActive: Boolean get() = this == CONFIRMED || this == IN_USE

    companion object {
        fun from(value: Any?): ReservationStatus? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Una reserva de un lugar para una franja: desde [arrivesAt] hasta [endsAt]. Ver
 * `docs/reservation-rules.md`.
 */
data class Reservation(
    val id: String,
    val userId: String,
    val parkingLotId: String,
    val spotId: String,
    val spotCode: String,
    val spotType: SpotType,
    val arrivesAt: Instant,
    val durationMinutes: Int,
    val endsAt: Instant,
    /** Hasta cuándo se puede hacer check-in; después vence. */
    val toleranceUntil: Instant,
    val status: ReservationStatus,
    val checkedInAt: Instant? = null,
    val checkedOutAt: Instant? = null,
    /** Minutos reales de la estadía, al hacer check-out. */
    val stayMinutes: Int? = null,
    /** Importe informativo en pesos, al hacer check-out. */
    val amount: Int? = null,
)

/**
 * Pedido de reserva. Se pide un tipo de lugar ([spotType]) o un lugar puntual ([spotId]); el
 * backend elige el lugar y confirma.
 */
data class ReservationRequest(
    val parkingLotId: String,
    val arrivesAt: Instant,
    val durationMinutes: Int,
    val spotType: SpotType? = null,
    val spotId: String? = null,
)

/** Lo que devuelve el backend al confirmar una reserva. */
data class ReservationConfirmation(
    val reservationId: String,
    val spotId: String,
    val spotCode: String,
    val arrivesAt: Instant,
    val endsAt: Instant,
    val toleranceUntil: Instant,
)

data class CheckOutSummary(val stayMinutes: Int, val amount: Int)

enum class WaitlistStatus { WAITING, LEFT }

/** Una entrada de la lista de espera: alguien que quiere un lugar de [spotType] para esa franja. */
data class WaitlistEntry(
    val id: String,
    val userId: String,
    val parkingLotId: String,
    val spotType: SpotType,
    val arrivesAt: Instant,
    val durationMinutes: Int,
    val category: Category,
    val requestedAt: Instant,
    val status: WaitlistStatus = WaitlistStatus.WAITING,
)

/** Resultado de anotarse: la entrada creada y en qué lugar de la fila quedó (1 es el primero). */
data class WaitlistPosition(val entryId: String, val position: Int)

/**
 * Motivos por los que el negocio rechaza una operación de reservas. Son los mismos `reason` que
 * devuelven las Cloud Functions; ver `docs/reservation-rules.md`.
 */
enum class ReservationRejection {
    INVALID_DURATION,
    IN_THE_PAST,
    TOO_FAR_AHEAD,
    OUTSIDE_SCHEDULE,
    CATEGORY_REQUIRED,
    LIMIT_REACHED,
    NO_SPOTS,
    CANCEL_NOT_ALLOWED,
    TOO_EARLY,
    EXPIRED,
    TOO_FAR,

    /** Check-out de una reserva que no está en uso. */
    INVALID_STATUS,
    ALREADY_IN_WAITLIST;

    companion object {
        fun from(value: Any?): ReservationRejection? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Resultado de una operación de reservas: salió bien, la rechazó una regla de negocio, o no se
 * pudo hacer (sin conexión, sin permiso...).
 */
sealed interface ReservationOutcome<out T> {
    data class Success<T>(val value: T) : ReservationOutcome<T>

    data class Rejected(val reason: ReservationRejection) : ReservationOutcome<Nothing>

    data class Failed(val reason: DataErrorReason) : ReservationOutcome<Nothing>
}
