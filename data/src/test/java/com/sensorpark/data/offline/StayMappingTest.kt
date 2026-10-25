package com.sensorpark.data.offline

import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StayMappingTest {
    private val arrivesAt = Instant.parse("2026-10-20T15:00:00Z")
    private val finished = Reservation(
        id = "r1",
        userId = "ana",
        parkingLotId = "utn-medrano",
        spotId = "07",
        spotCode = "07",
        spotType = SpotType.STANDARD,
        arrivesAt = arrivesAt,
        durationMinutes = 60,
        endsAt = arrivesAt.plusSeconds(3600),
        toleranceUntil = arrivesAt.plusSeconds(900),
        status = ReservationStatus.FINISHED,
        checkedInAt = arrivesAt,
        checkedOutAt = arrivesAt.plusSeconds(65 * 60),
        stayMinutes = 65,
        amount = 2_708,
    )
    private val names = mapOf("utn-medrano" to "UTN Medrano")

    @Test
    fun `una reserva terminada es una estadia con su duracion e importe`() {
        val stay = finished.toStay(names)!!
        assertEquals("UTN Medrano", stay.parkingLotName)
        assertEquals(65, stay.stayMinutes)
        assertEquals(2_708, stay.amount)
        assertEquals(arrivesAt.plusSeconds(65 * 60), stay.checkedOutAt)
    }

    @Test
    fun `las que no terminaron o no tienen nombre de estacionamiento no se guardan`() {
        assertNull(finished.copy(status = ReservationStatus.CANCELLED).toStay(names))
        assertNull(finished.copy(status = ReservationStatus.IN_USE).toStay(names))
        assertNull(finished.toStay(emptyMap()))
    }
}
