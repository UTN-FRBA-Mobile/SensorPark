package com.sensorpark.core.testing

import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Schedule
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Datos de ejemplo para tests y para probar la app a mano, con los mismos nombres que la semilla
 * de `functions/seed.js`. Así un test se lee por lo que prueba y no por cómo arma los datos.
 */
object Samples {
    /** Una hora fija: el mediodía argentino del 18/10/2026. */
    val NOW: Instant = Instant.parse("2026-10-18T15:00:00Z")

    val UTN_MEDRANO = ParkingLot(
        id = "utn-medrano",
        name = "UTN Medrano",
        address = "Av. Medrano 951",
        location = Coordinates(-34.5984, -58.4200),
        schedule = Schedule(LocalTime.of(0, 0), LocalTime.of(23, 59)),
        toleranceMinutes = 15,
        geofenceRadiusMeters = 300,
        hourlyRate = 1_200,
        capacity = 14,
        freeSpots = 9,
        capacityByType = mapOf(SpotType.STANDARD to 10, SpotType.PRIORITY to 2, SpotType.MOTORCYCLE to 2),
        freeSpotsByType = mapOf(SpotType.STANDARD to 7, SpotType.PRIORITY to 1, SpotType.MOTORCYCLE to 1),
    )

    fun spot(code: String = "07", status: SpotStatus = SpotStatus.FREE, type: SpotType = SpotType.STANDARD) =
        Spot(id = "spot-$code", code = code, type = type, status = status)

    fun availability(vararg spots: Spot, isFromCache: Boolean = false) =
        SpotAvailability(spots = spots.toList(), isFromCache = isFromCache, lastUpdate = NOW)

    /**
     * Una reserva en el estacionamiento de ejemplo. Por defecto llega en diez minutos, que es la
     * situación de la demo: ya se puede hacer check-in.
     */
    fun reservation(
        id: String = "res-1",
        status: ReservationStatus = ReservationStatus.CONFIRMED,
        arrivesAt: Instant = NOW.plus(Duration.ofMinutes(10)),
        durationMinutes: Int = 60,
        spotCode: String = "07",
        toleranceMinutes: Long = 15,
    ) = Reservation(
        id = id,
        userId = "ana",
        parkingLotId = UTN_MEDRANO.id,
        spotId = "spot-$spotCode",
        spotCode = spotCode,
        spotType = SpotType.STANDARD,
        arrivesAt = arrivesAt,
        durationMinutes = durationMinutes,
        endsAt = arrivesAt.plus(Duration.ofMinutes(durationMinutes.toLong())),
        toleranceUntil = arrivesAt.plus(Duration.ofMinutes(toleranceMinutes)),
        status = status,
    )
}
