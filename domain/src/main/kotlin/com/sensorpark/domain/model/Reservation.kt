package com.sensorpark.domain.model

import java.time.Duration
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

/** El lugar nuevo que da el backend cuando el asignado apareció ocupado por otro auto. */
data class SpotReassignment(val spotId: String, val spotCode: String)

/** Estados de una entrada de la lista de espera; ver `docs/reservation-rules.md`. */
enum class WaitlistStatus {
    /** En la fila, esperando que se libere un lugar. */
    WAITING,

    /** Se liberó un lugar y se lo están guardando unos minutos: tiene que aceptar o rechazar. */
    OFFERED,

    /** Aceptó la oferta: ahora tiene una reserva. */
    ACCEPTED,

    /** Rechazó la oferta o la dejó vencer. */
    DECLINED,
    LEFT,

    /** Su franja empezó sin que se liberara nada. */
    EXPIRED;

    /** Las abiertas son las que siguen en la fila: esperando o con una oferta para contestar. */
    val isOpen: Boolean get() = this == WAITING || this == OFFERED

    companion object {
        fun from(value: Any?): WaitlistStatus? = entries.firstOrNull { it.name == value }
    }
}

/** El lugar que se le guarda al primero de la fila hasta [expiresAt]. */
data class WaitlistOffer(
    val spotId: String,
    val spotCode: String,
    val offeredAt: Instant,
    val expiresAt: Instant,
)

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
    /** Lugar en la fila (1 es el primero); sólo mientras espera. */
    val position: Int? = null,
    val offer: WaitlistOffer? = null,
    /** La reserva que salió de aceptar la oferta. */
    val reservationId: String? = null,
) {
    val endsAt: Instant get() = arrivesAt.plus(Duration.ofMinutes(durationMinutes.toLong()))
}

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
    ALREADY_IN_WAITLIST,

    /** Se pidió otro lugar, pero el asignado no figura ocupado por otro auto. */
    SPOT_NOT_TAKEN,

    /** Aceptar o rechazar una entrada de la lista de espera que no tiene una oferta pendiente. */
    NO_OFFER,

    /** Se pasaron los minutos de la oferta: el lugar ya pasó al siguiente de la fila. */
    OFFER_EXPIRED,

    /** Sacar de servicio o borrar un lugar con una reserva activa o una oferta de la lista de espera. */
    SPOT_HAS_RESERVATION;

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
