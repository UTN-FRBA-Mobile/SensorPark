package com.sensorpark.domain.model

import com.sensorpark.domain.reservation.ReservationRules
import java.time.Instant

/**
 * Una geocerca registrada en el teléfono. Guarda lo mínimo para dos cosas que tienen que andar sin
 * red y sin sesión: volver a registrarla después de un reinicio, y armar el aviso de llegada con
 * el nombre del estacionamiento y el lugar sin consultar al backend.
 *
 * Las reservas en sí no se guardan en el teléfono: se leen de Firestore.
 */
data class ActiveGeofence(
    val reservationId: String,
    val parkingLotName: String,
    val spotCode: String,
    val center: Coordinates,
    val radiusMeters: Int,
    /** Cuándo deja de tener sentido: el fin de la tolerancia de la reserva. */
    val expiresAt: Instant,
    /** La hora de llegada de la reserva. */
    val arrivesAt: Instant,
) {
    /**
     * Si entrar al círculo ahora tiene que avisar la llegada: sólo cuando ya se puede hacer el
     * check-in y todavía no venció. Pasar por la puerta el día anterior no es llegar.
     */
    fun announcesArrival(now: Instant): Boolean =
        now >= ReservationRules.checkInOpensAt(arrivesAt) && now <= expiresAt
}

/** Una lectura del GPS: dónde está el teléfono y con cuánto margen de error. */
data class LocationReading(val coordinates: Coordinates, val accuracyMeters: Float)
