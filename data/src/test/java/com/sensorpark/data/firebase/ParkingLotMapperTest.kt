package com.sensorpark.data.firebase

import com.google.firebase.firestore.GeoPoint
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.Schedule
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class ParkingLotMapperTest {
    // Como lo deja la semilla (functions/seed.js) después de ocupar el lugar 01. Firestore entrega los enteros como Long.
    private val seeded: Map<String, Any?> = mapOf(
        "name" to "UTN Medrano",
        "address" to "Av. Medrano 951, CABA",
        "location" to GeoPoint(-34.5984, -58.42),
        "schedule" to mapOf("opensAt" to "06:00", "closesAt" to "23:00"),
        "toleranceMinutes" to 15L,
        "geofenceRadiusMeters" to 300L,
        "hourlyRate" to 2500L,
        "adminUids" to listOf("encargado"),
        "capacity" to 14L,
        "freeSpots" to 13L,
        "capacityByType" to mapOf("STANDARD" to 8L, "PRIORITY" to 2L, "MOTORCYCLE" to 2L, "EV_CHARGING" to 2L),
        "freeSpotsByType" to mapOf("STANDARD" to 7L, "PRIORITY" to 2L, "MOTORCYCLE" to 2L, "EV_CHARGING" to 2L),
    )

    @Test
    fun `convierte un estacionamiento completo`() {
        val lot = parkingLotFrom("utn-medrano", seeded)!!

        assertEquals("UTN Medrano", lot.name)
        assertEquals(Coordinates(-34.5984, -58.42), lot.location)
        assertEquals(Schedule(LocalTime.of(6, 0), LocalTime.of(23, 0)), lot.schedule)
        assertEquals(15, lot.toleranceMinutes)
        assertEquals(2500, lot.hourlyRate)
        assertEquals(listOf("encargado"), lot.adminUids)
        assertEquals(14, lot.capacity)
        assertEquals(13, lot.freeSpots)
        assertEquals(7, lot.freeSpotsOf(SpotType.STANDARD))
        assertEquals(2, lot.capacityOf(SpotType.EV_CHARGING))
    }

    @Test
    fun `recien creado, sin contadores ni horario, queda en cero`() {
        val lot = parkingLotFrom("nuevo", mapOf("name" to "Nuevo", "location" to GeoPoint(-34.6, -58.4)))!!

        assertEquals(0, lot.capacity)
        assertEquals(0, lot.freeSpots)
        assertNull(lot.schedule)
        assertEquals(0, lot.freeSpotsOf(SpotType.STANDARD))
    }

    @Test
    fun `sin nombre o sin ubicacion se descarta`() {
        assertNull(parkingLotFrom("x", seeded - "name"))
        assertNull(parkingLotFrom("x", seeded - "location"))
    }

    @Test
    fun `ignora los tipos de lugar que no conoce`() {
        val lot = parkingLotFrom("x", seeded + ("freeSpotsByType" to mapOf("STANDARD" to 1L, "VALET" to 3L)))!!

        assertEquals(mapOf(SpotType.STANDARD to 1), lot.freeSpotsByType)
    }

    @Test
    fun `un horario mal escrito no rompe la lectura`() {
        val lot = parkingLotFrom("x", seeded + ("schedule" to mapOf("opensAt" to "6hs", "closesAt" to "23:00")))!!

        assertNull(lot.schedule)
    }

    @Test
    fun `convierte un lugar y descarta los de tipo o estado desconocido`() {
        val spot = spotFrom("01", mapOf("code" to "01", "type" to "PRIORITY", "status" to "RESERVED"))!!

        assertEquals(SpotType.PRIORITY, spot.type)
        assertEquals(SpotStatus.RESERVED, spot.status)
        assertNull(spotFrom("02", mapOf("type" to "VALET", "status" to "FREE")))
        assertNull(spotFrom("03", mapOf("type" to "STANDARD", "status" to "BROKEN")))
    }
}
