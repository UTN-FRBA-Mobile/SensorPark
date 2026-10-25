package com.sensorpark.domain.model

import java.time.Instant

/** Una estadía terminada: una reserva con check-out, como la guarda el historial (T19). */
data class Stay(
    val reservationId: String,
    val parkingLotId: String,
    val parkingLotName: String,
    val spotCode: String,
    val arrivesAt: Instant,
    val checkedOutAt: Instant?,
    val stayMinutes: Int,
    /** Importe informativo en pesos. */
    val amount: Int,
)
