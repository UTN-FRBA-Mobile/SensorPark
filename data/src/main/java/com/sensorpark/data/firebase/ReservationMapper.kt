package com.sensorpark.data.firebase

import com.google.firebase.Timestamp
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.model.WaitlistOffer
import com.sensorpark.domain.model.WaitlistStatus
import java.time.Instant
import java.time.OffsetDateTime

// Conversión de reservas y entradas de la lista de espera entre Firestore o las functions y los
// modelos de domain. Como en ParkingLotMapper, recibe mapas para poder probarla sin Firebase.

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

/** Una entrada de la lista de espera. Sin fechas, estacionamiento o estado reconocibles, se descarta. */
internal fun waitlistEntryFrom(id: String, data: Map<String, Any?>): WaitlistEntry? {
    val offer = (data[WaitlistFields.OFFER] as? Map<*, *>)?.let { offer ->
        WaitlistOffer(
            spotId = offer[WaitlistFields.OFFER_SPOT_ID] as? String ?: return@let null,
            spotCode = offer[WaitlistFields.OFFER_SPOT_CODE] as? String ?: "",
            offeredAt = instant(offer[WaitlistFields.OFFER_OFFERED_AT]) ?: return@let null,
            expiresAt = instant(offer[WaitlistFields.OFFER_EXPIRES_AT]) ?: return@let null,
        )
    }
    return WaitlistEntry(
        id = id,
        userId = data[WaitlistFields.USER_ID] as? String ?: return null,
        parkingLotId = data[WaitlistFields.PARKING_LOT_ID] as? String ?: return null,
        spotType = SpotType.from(data[WaitlistFields.SPOT_TYPE]) ?: SpotType.STANDARD,
        arrivesAt = instant(data[WaitlistFields.ARRIVES_AT]) ?: return null,
        durationMinutes = (data[WaitlistFields.DURATION_MINUTES] as? Number)?.toInt() ?: return null,
        category = Category.from(data[WaitlistFields.CATEGORY]),
        requestedAt = instant(data[WaitlistFields.REQUESTED_AT]) ?: return null,
        status = WaitlistStatus.from(data[WaitlistFields.STATUS]) ?: return null,
        position = (data[WaitlistFields.POSITION] as? Number)?.toInt(),
        offer = offer,
        reservationId = data[WaitlistFields.RESERVATION_ID] as? String,
    )
}

/** Firestore entrega Timestamp; las functions, texto ISO 8601. */
private fun instant(value: Any?): Instant? = when (value) {
    is Timestamp -> Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong())
    // OffsetDateTime acepta tanto "Z" como "-03:00"; Instant.parse, en Android viejo, sólo "Z".
    is String -> runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    else -> null
}
