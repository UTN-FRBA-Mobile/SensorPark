package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotReassignment
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.notification.InMemoryNotificationRepository
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReminderScheduler
import com.sensorpark.domain.repository.ReservationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SyncReservationAlertsUseCaseTest {

    @Test
    fun unaReservaNuevaAvisaProgramaElRecordatorioYRegistraLaGeocerca() = runTest {
        val world = World()

        world.sync(listOf(reservation()))

        assertEquals(listOf("confirmed$RESERVATION_ID"), world.notifications.shown.value.map { it.key })
        // 30 minutos antes de las 15:00Z.
        assertEquals(Instant.parse("2026-10-18T14:30:00Z"), world.reminders.scheduled[RESERVATION_ID])
        assertEquals(listOf(RESERVATION_ID), world.geofences.active().map { it.reservationId })
    }

    @Test
    fun correrDosVecesNoRepiteElAvisoDeConfirmacion() = runTest {
        val world = World()
        val mine = listOf(reservation())

        world.sync(mine)
        world.sync(mine)

        assertEquals(1, world.notifications.shown.value.size)
    }

    @Test
    fun sinPermisoDeSegundoPlanoIgualNoRepiteElAviso() = runTest {
        val world = World()
        world.geofences.grantBackgroundPermission = false
        val mine = listOf(reservation())

        world.sync(mine)
        world.sync(mine)

        assertEquals(1, world.notifications.shown.value.size)
    }

    @Test
    fun alCancelarseLaReservaSeQuitanLaGeocercaYElRecordatorio() = runTest {
        val world = World()
        world.sync(listOf(reservation()))

        world.sync(listOf(reservation(status = ReservationStatus.CANCELLED)))

        assertTrue(world.geofences.active().isEmpty())
        assertEquals(listOf(RESERVATION_ID), world.reminders.cancelled)
    }

    @Test
    fun alHacerCheckInSeQuitaLaGeocercaYApareceElAvisoPersistente() = runTest {
        val world = World()
        world.sync(listOf(reservation()))

        world.sync(listOf(reservation(status = ReservationStatus.IN_USE)))

        assertTrue(world.geofences.active().isEmpty())
        val stay = world.notifications.shown.value.firstOrNull { it.key == "stay$RESERVATION_ID" }
        assertTrue(stay?.ongoing == true)
    }

    @Test
    fun alTerminarLaEstadiaSeSacaElAvisoPersistente() = runTest {
        val world = World()
        world.sync(listOf(reservation(status = ReservationStatus.IN_USE)))

        world.sync(listOf(reservation(status = ReservationStatus.FINISHED)))

        assertNull(world.notifications.shown.value.firstOrNull { it.key == "stay$RESERVATION_ID" })
    }

    @Test
    fun unaGeocercaDeUnaReservaQueYaNoEstaSeLimpia() = runTest {
        val world = World()
        world.sync(listOf(reservation()))

        world.sync(emptyList())

        assertTrue(world.geofences.active().isEmpty())
        assertEquals(listOf(RESERVATION_ID), world.reminders.cancelled)
    }

    @Test
    fun unaReservaConLaToleranciaVencidaNoRegistraNada() = runTest {
        // 14:40Z: la tolerancia terminó 14:30Z.
        val world = World(now = Instant.parse("2026-10-18T14:40:00Z"))

        world.sync(listOf(reservation()))

        assertTrue(world.geofences.active().isEmpty())
        assertTrue(world.notifications.shown.value.isEmpty())
    }

    private class World(now: Instant = Instant.parse("2026-10-18T14:00:00Z")) {
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val geofences = RecordingGeofenceRepository()
        val reminders = RecordingReminderScheduler()
        val notifications = InMemoryNotificationRepository()

        private val useCase = SyncReservationAlertsUseCase(
            reservations = UnusedReservationRepository,
            parkingLots = UnusedParkingLotRepository,
            geofences = geofences,
            reminders = reminders,
            notifications = notifications,
            messages = TestAlertMessages,
            clock = clock,
        )

        suspend fun sync(mine: List<Reservation>) = useCase.sync(mine, mapOf(LOT.id to LOT))
    }

    private companion object {
        const val RESERVATION_ID = "res-1"

        val LOT = ParkingLot(
            id = "utn-medrano",
            name = "UTN Medrano",
            address = "Medrano 951",
            location = Coordinates(-34.5984, -58.4200),
            toleranceMinutes = 15,
            geofenceRadiusMeters = 300,
        )

        fun reservation(status: ReservationStatus = ReservationStatus.CONFIRMED) = Reservation(
            id = RESERVATION_ID,
            userId = "user-1",
            parkingLotId = LOT.id,
            spotId = "spot-07",
            spotCode = "07",
            spotType = SpotType.STANDARD,
            arrivesAt = Instant.parse("2026-10-18T15:00:00Z"),
            durationMinutes = 60,
            endsAt = Instant.parse("2026-10-18T16:00:00Z"),
            toleranceUntil = Instant.parse("2026-10-18T14:30:00Z"),
            status = status,
        )
    }
}

private class RecordingGeofenceRepository : GeofenceRepository {
    private val geofences = mutableMapOf<String, ActiveGeofence>()
    var grantBackgroundPermission = true

    override suspend fun register(geofence: ActiveGeofence): Boolean {
        // Como la implementación real: la fila se guarda aunque el permiso falte.
        geofences[geofence.reservationId] = geofence
        return grantBackgroundPermission
    }

    override suspend fun remove(reservationId: String) {
        geofences -= reservationId
    }

    override suspend fun active(): List<ActiveGeofence> = geofences.values.toList()

    override suspend fun find(reservationId: String): ActiveGeofence? = geofences[reservationId]

    override suspend fun restoreAll() = Unit

    override fun hasBackgroundLocationPermission(): Boolean = grantBackgroundPermission
}

private class RecordingReminderScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<String, Instant>()
    val cancelled = mutableListOf<String>()

    override suspend fun scheduleArrivalReminder(reservationId: String, notifyAt: Instant) {
        scheduled[reservationId] = notifyAt
    }

    override suspend fun cancel(reservationId: String) {
        cancelled += reservationId
    }
}

private object TestAlertMessages : ReservationAlertMessages {
    override fun reservationConfirmed(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ) = AppNotification(NotificationType.RESERVATION, "confirmed$reservationId", "Reserva", parkingLotName)

    override fun arrivalReminder(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ) = AppNotification(NotificationType.REMINDER, "reminder$reservationId", "Recordatorio", parkingLotName)

    override fun arrival(geofence: ActiveGeofence) =
        AppNotification(NotificationType.ARRIVAL, "arrival${geofence.reservationId}", "Llegaste", geofence.spotCode)

    override fun stay(reservationId: String, spotCode: String, endsAt: Instant) = AppNotification(
        NotificationType.RESERVATION,
        stayKey(reservationId),
        "Estacionado",
        spotCode,
        ongoing = true,
    )

    override fun stayKey(reservationId: String) = "stay$reservationId"
}

/** La reconciliación se prueba con `sync`, que recibe las listas: estos no se usan. */
private object UnusedReservationRepository : ReservationRepository {
    override fun myReservations(): Flow<DataResult<List<Reservation>>> = flowOf(DataResult.Success(emptyList()))

    override suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation> =
        throw UnsupportedOperationException()

    override suspend fun cancel(reservationId: String): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()

    override suspend fun reassignSpot(reservationId: String): ReservationOutcome<SpotReassignment> =
        throw UnsupportedOperationException()

    override suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()

    override suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary> =
        throw UnsupportedOperationException()

    override suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition> =
        throw UnsupportedOperationException()

    override suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()
}

private object UnusedParkingLotRepository : ParkingLotRepository {
    override fun parkingLots(): Flow<DataResult<List<ParkingLot>>> = flowOf(DataResult.Success(emptyList()))

    override fun parkingLot(id: String): Flow<DataResult<ParkingLot>> =
        throw UnsupportedOperationException()
}
