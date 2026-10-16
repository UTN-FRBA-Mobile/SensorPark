package com.sensorpark.data.firebase

import com.google.firebase.Timestamp
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotType
import java.time.Instant
import java.time.OffsetDateTime

// Conversión de reservas entre Firestore o las functions y los modelos de domain. Como en
// ParkingLotMapper, recibe mapas para poder probarla sin Firebase.

/** Una reserva de Firestore. Sin fechas, lugar o estado reconocibles, se descarta. */
internal fun reservationFrom(id: String, data: Map<String, Any?>): Reservation? {
    val arrivesAt = instant(data[ReservationFields.ARRIVES_AT]) ?: return null
    return Reservation(
        id = id,
        userId = data[ReservationFields.USER_ID] as? String ?: return null,
        parkingLotId = data[ReservationFields.PARKING_LOT_ID] as? String ?: return null,
        spotId = data[ReservationFields.SPOT_ID] as? String ?: return null,
        spotCode = data[ReservationFields.SPOT_CODE] as? String ?: "",
        spotType = SpotType.from(data[ReservationFields.SPOT_TYPE]) ?: SpotType.STANDARD,
        arrivesAt = arrivesAt,
        durationMinutes = (data[ReservationFields.DURATION_MINUTES] as? Number)?.toInt() ?: return null,
        endsAt = instant(data[ReservationFields.ENDS_AT]) ?: return null,
        toleranceUntil = instant(data[ReservationFields.TOLERANCE_UNTIL]) ?: arrivesAt,
        status = ReservationStatus.from(data[ReservationFields.STATUS]) ?: return null,
        checkedInAt = instant(data[ReservationFields.CHECKED_IN_AT]),
        checkedOutAt = instant(data[ReservationFields.CHECKED_OUT_AT]),
        stayMinutes = (data[ReservationFields.STAY_MINUTES] as? Number)?.toInt(),
        amount = (data[ReservationFields.AMOUNT] as? Number)?.toInt(),
    )
}

/** La respuesta de `createReservation`, con las fechas en ISO 8601. */
internal fun confirmationFrom(data: Map<*, *>): ReservationConfirmation? {
    return ReservationConfirmation(
        reservationId = data["reservationId"] as? String ?: return null,
        spotId = data["spotId"] as? String ?: return null,
        spotCode = data["spotCode"] as? String ?: "",
        arrivesAt = instant(data["arrivesAt"]) ?: return null,
        endsAt = instant(data["endsAt"]) ?: return null,
        toleranceUntil = instant(data["toleranceUntil"]) ?: return null,
    )
}

/** Firestore entrega Timestamp; las functions, texto ISO 8601. */
private fun instant(value: Any?): Instant? = when (value) {
    is Timestamp -> Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong())
    // OffsetDateTime acepta tanto "Z" como "-03:00"; Instant.parse, en Android viejo, sólo "Z".
    is String -> runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    else -> null
}
