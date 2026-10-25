package com.sensorpark.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class AdminDraftsTest {
    private val valid = ParkingLotDraft(
        name = "Playa Medrano",
        address = "Av. Medrano 951",
        latitude = -34.5984,
        longitude = -58.42,
        opensAt = LocalTime.of(7, 0),
        closesAt = LocalTime.of(22, 0),
    )

    @Test
    fun `un estacionamiento completo se puede guardar`() {
        assertTrue(valid.problems().isEmpty())
        assertTrue("sin horario también", valid.copy(opensAt = null, closesAt = null).problems().isEmpty())
    }

    @Test
    fun `marca cada campo con problemas`() {
        val problems = ParkingLotDraft(
            name = " ",
            latitude = -134.0,
            longitude = -58.0,
            opensAt = LocalTime.of(22, 0),
            closesAt = LocalTime.of(7, 0),
            toleranceMinutes = 90,
            geofenceRadiusMeters = 10,
            hourlyRate = -1,
        ).problems()
        assertEquals(ParkingLotField.entries.toSet(), problems)
    }

    @Test
    fun `una sola hora no alcanza para el horario`() {
        assertEquals(setOf(ParkingLotField.SCHEDULE), valid.copy(closesAt = null).problems())
    }

    @Test
    fun `un lugar necesita codigo propio y los preferenciales su categoria`() {
        assertEquals(setOf(SpotProblem.CODE_REQUIRED), SpotDraft(code = " ").problems(emptySet()))
        assertEquals(setOf(SpotProblem.CODE_TAKEN), SpotDraft(code = "07").problems(setOf("07")))
        assertEquals(
            setOf(SpotProblem.CATEGORY_REQUIRED),
            SpotDraft(code = "09", type = SpotType.PRIORITY).problems(emptySet()),
        )
        assertTrue(SpotDraft("09", SpotType.PRIORITY, Category.PREGNANT).problems(emptySet()).isEmpty())
    }

    @Test
    fun `el codigo siguiente sigue la numeracion`() {
        assertEquals("15", SpotDraft.nextCode(listOf("01", "14", "07")))
        assertEquals("01", SpotDraft.nextCode(emptyList()))
    }

    @Test
    fun `la ocupacion por hora cuenta las franjas que tocan cada hora`() {
        val zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires")
        val day = java.time.LocalDate.of(2026, 10, 25)
        fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant()
        fun reservation(id: String, from: Instant, until: Instant, status: ReservationStatus, checkedOut: Instant? = null) =
            Reservation(id, "ana", "lot", "01", "01", SpotType.STANDARD, from, 60, until, from, status, checkedOutAt = checkedOut)

        val hours = hourlyOccupancy(
            listOf(
                // De 10:30 a 12:00: toca las 10 y las 11.
                reservation("a", at(10, 30), at(12), ReservationStatus.FINISHED),
                // Reservó de 11 a 13 pero se fue a las 11:20: sólo cuenta las 11.
                reservation("b", at(11), at(13), ReservationStatus.FINISHED, checkedOut = at(11, 20)),
                reservation("c", at(11), at(12), ReservationStatus.CANCELLED),
                reservation("d", at(11), at(12), ReservationStatus.EXPIRED),
            ),
            day,
            zone,
        )

        assertEquals(24, hours.size)
        assertEquals(1, hours[10])
        assertEquals(2, hours[11])
        assertEquals(0, hours[12])
        assertEquals(3, hours.sum())
    }

    @Test
    fun `el resumen del dia cuenta lugares y reservas`() {
        val spots = listOf(
            Spot("01", "01", SpotType.STANDARD, SpotStatus.FREE),
            Spot("02", "02", SpotType.STANDARD, SpotStatus.OCCUPIED),
            Spot("03", "03", SpotType.STANDARD, SpotStatus.OUT_OF_SERVICE),
        )
        val now = Instant.parse("2026-10-25T15:00:00Z")
        fun reservation(status: ReservationStatus) =
            Reservation("r-$status", "ana", "lot", "01", "01", SpotType.STANDARD, now, 60, now, now, status)
        val summary = DaySummary.of(
            spots,
            listOf(
                reservation(ReservationStatus.CONFIRMED),
                reservation(ReservationStatus.FINISHED),
                reservation(ReservationStatus.CANCELLED),
            ),
        )
        assertEquals(
            DaySummary(1, 1, 0, 1, reservations = 3, active = 1, finished = 1, cancelled = 1, expired = 0),
            summary,
        )
    }
}
