package com.sensorpark.domain.reservation

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Schedule
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/** Los ejemplos de `docs/reservation-rules.md`, del lado de la app. */
class ReservationRulesTest {
    private val lot = ParkingLot(
        id = "utn-medrano",
        name = "UTN Medrano",
        address = "",
        location = Coordinates(-34.5984, -58.4200),
        schedule = Schedule(LocalTime.of(6, 0), LocalTime.of(23, 0)),
        toleranceMinutes = 15,
        geofenceRadiusMeters = 300,
        hourlyRate = 2500,
    )

    /** Lunes 19/10/2026 a las 12:00, hora argentina. */
    private val now = at(19, 12, 0)

    private fun at(day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(ReservationRules.ZONE).toInstant()

    private fun request(
        arrivesAt: Instant = now.plus(Duration.ofHours(1)),
        durationMinutes: Int = 60,
        spotType: SpotType? = SpotType.STANDARD,
        spotId: String? = null,
    ) = ReservationRequest("utn-medrano", arrivesAt, durationMinutes, spotType, spotId)

    private fun validate(request: ReservationRequest, category: Category = Category.STANDARD, active: Int = 0) =
        ReservationRules.validateRequest(request, lot, category, active, now)

    private fun spot(
        id: String,
        type: SpotType = SpotType.STANDARD,
        status: SpotStatus = SpotStatus.FREE,
        priorityCategory: Category? = null,
    ) = Spot(id, id, type, status, priorityCategory)

    private fun reservation(
        spotId: String,
        arrivesAt: Instant,
        durationMinutes: Int = 60,
        status: ReservationStatus = ReservationStatus.CONFIRMED,
    ) = Reservation(
        id = "r-$spotId-$arrivesAt",
        userId = "otro",
        parkingLotId = "utn-medrano",
        spotId = spotId,
        spotCode = spotId,
        spotType = SpotType.STANDARD,
        arrivesAt = arrivesAt,
        durationMinutes = durationMinutes,
        endsAt = ReservationRules.endsAt(arrivesAt, durationMinutes),
        toleranceUntil = arrivesAt.plus(Duration.ofMinutes(15)),
        status = status,
    )

    // Validación del pedido

    @Test
    fun `un pedido valido pasa`() = assertNull(validate(request()))

    @Test
    fun `una duracion que no es de la lista se rechaza`() {
        assertEquals(ReservationRejection.INVALID_DURATION, validate(request(durationMinutes = 45)))
        ReservationRules.ALLOWED_DURATIONS_MINUTES.forEach { minutes ->
            assertNull(validate(request(arrivesAt = at(19, 13, 0), durationMinutes = minutes)))
        }
    }

    @Test
    fun `una llegada hace 10 minutos esta en el pasado, hace 3 minutos todavia se acepta`() {
        assertEquals(ReservationRejection.IN_THE_PAST, validate(request(arrivesAt = now.minus(Duration.ofMinutes(10)))))
        assertNull(validate(request(arrivesAt = now.minus(Duration.ofMinutes(3)))))
    }

    @Test
    fun `una llegada dentro de 8 dias es demasiado a futuro`() =
        assertEquals(ReservationRejection.TOO_FAR_AHEAD, validate(request(arrivesAt = now.plus(Duration.ofDays(8)))))

    @Test
    fun `la franja tiene que caer dentro del horario`() {
        assertEquals(ReservationRejection.OUTSIDE_SCHEDULE, validate(request(arrivesAt = at(19, 22, 30))))
        assertEquals(ReservationRejection.OUTSIDE_SCHEDULE, validate(request(arrivesAt = at(20, 5, 30))))
        assertNull(validate(request(arrivesAt = at(19, 22, 0))))
    }

    @Test
    fun `sin horario cargado no hay restriccion`() =
        assertTrue(ReservationRules.fitsSchedule(lot.copy(schedule = null), at(20, 2, 0), at(20, 3, 0)))

    @Test
    fun `pedir un preferencial exige una categoria especial`() {
        assertEquals(ReservationRejection.CATEGORY_REQUIRED, validate(request(spotType = SpotType.PRIORITY)))
        assertNull(validate(request(spotType = SpotType.PRIORITY), category = Category.PREGNANT))
    }

    @Test
    fun `con 2 reservas activas se alcanza el limite`() =
        assertEquals(ReservationRejection.LIMIT_REACHED, validate(request(), active = 2))

    @Test
    fun `se valida en el orden del documento`() =
        assertEquals(
            ReservationRejection.INVALID_DURATION,
            validate(request(durationMinutes = 45, spotType = SpotType.PRIORITY), active = 2),
        )

    // Superposición y elección de lugar

    @Test
    fun `franjas que se tocan no se superponen, por un minuto si`() {
        assertFalse(ReservationRules.overlaps(at(19, 13, 0), at(19, 14, 0), at(19, 14, 0), at(19, 15, 0)))
        assertTrue(ReservationRules.overlaps(at(19, 13, 0), at(19, 14, 0), at(19, 13, 59), at(19, 14, 59)))
    }

    @Test
    fun `el unico lugar reservado de 13 a 14 sirve de 14 a 15 pero no de 13 59 a 14 59`() {
        val spots = listOf(spot("01"))
        val taken = listOf(reservation("01", at(19, 13, 0)))

        assertEquals("01", ReservationRules.pickSpot(request(arrivesAt = at(19, 14, 0)), spots, taken, Category.STANDARD, now)?.id)
        assertNull(ReservationRules.pickSpot(request(arrivesAt = at(19, 13, 59)), spots, taken, Category.STANDARD, now))
    }

    @Test
    fun `una reserva cancelada o vencida no toma el lugar`() {
        val taken = listOf(
            reservation("01", at(19, 13, 0), status = ReservationStatus.CANCELLED),
            reservation("01", at(19, 13, 0), status = ReservationStatus.EXPIRED),
        )
        assertEquals("01", ReservationRules.pickSpot(request(arrivesAt = at(19, 13, 0)), listOf(spot("01")), taken, Category.STANDARD, now)?.id)
    }

    @Test
    fun `una reserva en uso que se paso de hora sigue ocupando hasta el check-out`() {
        val overstayed = reservation("01", at(19, 10, 0), status = ReservationStatus.IN_USE)
        assertEquals(now.plus(Duration.ofMinutes(15)), ReservationRules.takenUntil(overstayed, now))
        assertNull(ReservationRules.pickSpot(request(arrivesAt = now), listOf(spot("01")), listOf(overstayed), Category.STANDARD, now))
    }

    @Test
    fun `entre los que sirven elige el de codigo mas bajo`() {
        val spots = listOf(spot("03"), spot("01"), spot("02"))
        assertEquals("01", ReservationRules.pickSpot(request(), spots, emptyList(), Category.STANDARD, now)?.id)
    }

    @Test
    fun `respeta el tipo y el lugar puntual pedidos`() {
        val spots = listOf(spot("01"), spot("11", SpotType.MOTORCYCLE))
        assertEquals("11", ReservationRules.pickSpot(request(spotType = SpotType.MOTORCYCLE), spots, emptyList(), Category.STANDARD, now)?.id)
        assertEquals("01", ReservationRules.pickSpot(request(spotType = null, spotId = "01"), spots, emptyList(), Category.STANDARD, now)?.id)
    }

    @Test
    fun `un preferencial es solo para su categoria`() {
        val spots = listOf(spot("10", SpotType.PRIORITY, priorityCategory = Category.REDUCED_MOBILITY))
        val priority = request(spotType = SpotType.PRIORITY)

        assertNull(ReservationRules.pickSpot(priority, spots, emptyList(), Category.PREGNANT, now))
        assertEquals("10", ReservationRules.pickSpot(priority, spots, emptyList(), Category.REDUCED_MOBILITY, now)?.id)
    }

    @Test
    fun `con los comunes llenos, un conductor sin categoria no usa un preferencial libre`() {
        val spots = listOf(spot("01"), spot("09", SpotType.PRIORITY, priorityCategory = Category.PREGNANT))
        val taken = listOf(reservation("01", at(19, 13, 0)))
        assertNull(ReservationRules.pickSpot(request(arrivesAt = at(19, 13, 0), spotType = null), spots, taken, Category.STANDARD, now))
    }

    @Test
    fun `un lugar fuera de servicio no sirve`() =
        assertNull(ReservationRules.pickSpot(request(), listOf(spot("01", status = SpotStatus.OUT_OF_SERVICE)), emptyList(), Category.STANDARD, now))

    @Test
    fun `para ahora el lugar tiene que estar libre, para manana no importa`() {
        val occupied = listOf(spot("01", status = SpotStatus.OCCUPIED))
        assertNull(ReservationRules.pickSpot(request(arrivesAt = now.plus(Duration.ofMinutes(5))), occupied, emptyList(), Category.STANDARD, now))
        assertEquals("01", ReservationRules.pickSpot(request(arrivesAt = at(20, 12, 0)), occupied, emptyList(), Category.STANDARD, now)?.id)
    }

    // Cancelar, check-in y check-out

    @Test
    fun `solo se cancela antes del check-in`() {
        assertNull(ReservationRules.cancelProblem(reservation("01", at(19, 13, 0))))
        assertEquals(
            ReservationRejection.CANCEL_NOT_ALLOWED,
            ReservationRules.cancelProblem(reservation("01", at(19, 13, 0), status = ReservationStatus.IN_USE)),
        )
    }

    @Test
    fun `check-in dentro del radio y de la ventana`() {
        val r = reservation("01", now.plus(Duration.ofMinutes(10)))
        assertNull(ReservationRules.checkInProblem(r, lot, lot.location, now))
    }

    @Test
    fun `check-in 20 minutos antes es demasiado temprano`() {
        val r = reservation("01", now.plus(Duration.ofMinutes(20)))
        assertEquals(ReservationRejection.TOO_EARLY, ReservationRules.checkInProblem(r, lot, lot.location, now))
    }

    @Test
    fun `check-in despues de la tolerancia o de una reserva que no esta confirmada`() {
        val late = reservation("01", now.minus(Duration.ofMinutes(20)))
        assertEquals(ReservationRejection.EXPIRED, ReservationRules.checkInProblem(late, lot, lot.location, now))
        val cancelled = reservation("01", now, status = ReservationStatus.CANCELLED)
        assertEquals(ReservationRejection.EXPIRED, ReservationRules.checkInProblem(cancelled, lot, lot.location, now))
    }

    @Test
    fun `check-in a 1 km del estacionamiento esta demasiado lejos`() {
        val abasto = Coordinates(-34.6037, -58.4108)
        assertEquals(ReservationRejection.TOO_FAR, ReservationRules.checkInProblem(reservation("01", now), lot, abasto, now))
    }

    @Test
    fun `check-out a los 90 minutos con tarifa de 2500 la hora`() {
        val minutes = ReservationRules.stayMinutes(at(19, 12, 0), at(19, 13, 30))
        assertEquals(90, minutes)
        assertEquals(3750, ReservationRules.amount(2500, minutes))
    }

    // Lista de espera

    @Test
    fun `la lista de espera pone primero las categorias especiales y despues por hora de pedido`() {
        fun entry(id: String, category: Category, minute: Int) = WaitlistEntry(
            id, "u-$id", "utn-medrano", SpotType.STANDARD, at(19, 13, 0), 60, category, at(19, 11, minute),
        )
        val queue = listOf(
            entry("comun-temprano", Category.STANDARD, 0),
            entry("movilidad", Category.REDUCED_MOBILITY, 20),
            entry("embarazo", Category.PREGNANT, 10),
            entry("comun-tarde", Category.STANDARD, 30),
        )
        assertEquals(
            listOf("embarazo", "movilidad", "comun-temprano", "comun-tarde"),
            queue.sortedWith(ReservationRules.waitlistOrder).map { it.id },
        )
    }
}
