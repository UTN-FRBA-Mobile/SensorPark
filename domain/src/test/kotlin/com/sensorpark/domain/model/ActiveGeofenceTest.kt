package com.sensorpark.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Cuándo entrar al círculo de una reserva cuenta como llegar (AV-02 de las pruebas funcionales). */
class ActiveGeofenceTest {
    private val arrivesAt = Instant.parse("2026-10-28T13:00:00Z")
    private val geofence = ActiveGeofence(
        reservationId = "res-1",
        parkingLotName = "UTN Medrano",
        spotCode = "01",
        center = Coordinates(-34.5984, -58.4200),
        radiusMeters = 300,
        expiresAt = arrivesAt.plusSeconds(15 * 60),
        arrivesAt = arrivesAt,
    )

    @Test
    fun conLaReservaParaOtroDiaNoAvisa() {
        assertFalse(geofence.announcesArrival(arrivesAt.minusSeconds(26 * 3600)))
    }

    @Test
    fun antesDeQueAbraElCheckInNoAvisa() {
        assertFalse(geofence.announcesArrival(arrivesAt.minusSeconds(16 * 60)))
    }

    @Test
    fun desdeQueAbreElCheckInHastaQueVenceAvisa() {
        assertTrue(geofence.announcesArrival(arrivesAt.minusSeconds(15 * 60)))
        assertTrue(geofence.announcesArrival(arrivesAt))
        assertTrue(geofence.announcesArrival(arrivesAt.plusSeconds(15 * 60)))
    }

    @Test
    fun vencidaNoAvisa() {
        assertFalse(geofence.announcesArrival(arrivesAt.plusSeconds(16 * 60)))
    }

    @Test
    fun unaGeocercaGuardadaAntesDeLaVersion3AvisaComoAntes() {
        val old = geofence.copy(arrivesAt = Instant.EPOCH)
        assertTrue(old.announcesArrival(arrivesAt.minusSeconds(26 * 3600)))
    }
}
