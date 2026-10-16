package com.sensorpark.data.firebase

import com.google.firebase.Timestamp
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ReservationMapperTest {
    private val arrivesAt = Instant.parse("2026-10-19T16:00:00Z")

    private fun ts(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)

    // Como la escribe createReservation y después checkOut.
    private val stored: Map<String, Any?> = mapOf(
        "userId" to "ana",
        "parkingLotId" to "utn-medrano",
        "spotId" to "09",
        "spotCode" to "09",
        "spotType" to "PRIORITY",
        "arrivesAt" to ts(arrivesAt),
        "durationMinutes" to 60L,
        "endsAt" to ts(arrivesAt.plusSeconds(3600)),
        "toleranceUntil" to ts(arrivesAt.plusSeconds(900)),
        "status" to "FINISHED",
        "checkedInAt" to ts(arrivesAt),
        "checkedOutAt" to ts(arrivesAt.plusSeconds(5400)),
        "stayMinutes" to 90L,
        "amount" to 3750L,
    )

    @Test
    fun `convierte una reserva terminada`() {
        val r = reservationFrom("r1", stored)!!

        assertEquals(SpotType.PRIORITY, r.spotType)
        assertEquals(arrivesAt, r.arrivesAt)
        assertEquals(arrivesAt.plusSeconds(3600), r.endsAt)
        assertEquals(60, r.durationMinutes)
        assertEquals(ReservationStatus.FINISHED, r.status)
        assertEquals(90, r.stayMinutes)
        assertEquals(3750, r.amount)
    }

    @Test
    fun `sin fechas o con un estado desconocido se descarta`() {
        assertNull(reservationFrom("r1", stored - "arrivesAt"))
        assertNull(reservationFrom("r1", stored + ("status" to "PAUSED")))
    }

    @Test
    fun `la respuesta de createReservation trae fechas ISO, en Z o con huso`() {
        val confirmation = confirmationFrom(
            mapOf(
                "reservationId" to "r1",
                "spotId" to "01",
                "spotCode" to "01",
                "arrivesAt" to "2026-10-19T16:00:00.000Z",
                "endsAt" to "2026-10-19T14:00:00-03:00",
                "toleranceUntil" to "2026-10-19T16:15:00Z",
            ),
        )!!
        assertEquals(arrivesAt, confirmation.arrivesAt)
        assertEquals(Instant.parse("2026-10-19T17:00:00Z"), confirmation.endsAt)
    }
}
