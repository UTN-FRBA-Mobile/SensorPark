package com.sensorpark.domain.model

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
)

/** Una lectura del GPS: dónde está el teléfono y con cuánto margen de error. */
data class LocationReading(val coordinates: Coordinates, val accuracyMeters: Float)
