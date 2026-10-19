package com.sensorpark.feature.checkin

import com.sensorpark.core.testing.FakeAlertMessages
import com.sensorpark.core.testing.FakeGeofenceRepository
import com.sensorpark.core.testing.FakeLocationRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReminderScheduler
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.LocationReading
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.notification.InMemoryNotificationRepository
import com.sensorpark.domain.usecase.CheckInUseCase
import com.sensorpark.domain.usecase.CheckOutUseCase
import com.sensorpark.domain.usecase.ReassignSpotUseCase
import com.sensorpark.domain.usecase.SyncReservationAlertsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Como la pantalla: recolecta el estado desde el principio, así los listeners están abiertos.
    private fun TestScope.collecting(vm: CheckInViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

    @Test
    fun dentroDelRadioHaceElCheckIn() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.location.reading = LocationReading(LOT_LOCATION, accuracyMeters = 8f)

        vm.checkIn()

        assertEquals(listOf(RESERVATION_ID), world.reservations.checkedIn)
        assertNull(vm.state.value.message)
    }

    @Test
    fun fueraDelRadioNoLlamaAlBackendYExplicaPorQue() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        // Unos dos kilómetros al norte del estacionamiento.
        world.location.reading = LocationReading(Coordinates(-34.5800, -58.4200), accuracyMeters = 8f)

        vm.checkIn()

        assertEquals(emptyList<String>(), world.reservations.checkedIn)
        assertEquals(CheckInMessage.TOO_FAR, vm.state.value.message)
    }

    @Test
    fun fueraDelRadioConGpsImprecisoLoAclaraEnElMensaje() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.location.reading = LocationReading(Coordinates(-34.5800, -58.4200), accuracyMeters = 120f)

        vm.checkIn()

        assertEquals(CheckInMessage.TOO_FAR_LOW_ACCURACY, vm.state.value.message)
    }

    @Test
    fun pasadaLaToleranciaNoSePuedeHacerCheckIn() = runTest {
        // 14:20Z: la tolerancia terminó 14:15Z.
        val world = World(now = Instant.parse("2026-10-18T14:20:00Z"))
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.location.reading = LocationReading(LOT_LOCATION, accuracyMeters = 8f)

        assertFalse(vm.state.value.canCheckIn(world.now))
        vm.checkIn()

        assertEquals(emptyList<String>(), world.reservations.checkedIn)
        assertEquals(CheckInMessage.EXPIRED, vm.state.value.message)
    }

    @Test
    fun sinPermisoDeUbicacionAvisaYNoSeRompe() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.location.denyPermission()

        vm.checkIn()

        assertEquals(CheckInMessage.NO_LOCATION_PERMISSION, vm.state.value.message)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun antesDelCheckInUnLugarOcupadoEsUnaIncidencia() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)

        world.spots.emit(availability(SpotStatus.OCCUPIED))

        assertTrue(vm.state.value.spotTaken)
        assertFalse(vm.state.value.spotConfirmed)
    }

    @Test
    fun despuesDelCheckInElSensorConfirmaElLugar() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)

        world.reservations.emit(listOf(reservation(status = ReservationStatus.IN_USE)))
        world.spots.emit(availability(SpotStatus.OCCUPIED))

        assertTrue(vm.state.value.spotConfirmed)
        assertFalse(vm.state.value.spotTaken)
    }

    @Test
    fun conElLugarTomadoPorOtroSePuedePedirOtroLugar() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.spots.emit(availability(SpotStatus.OCCUPIED))

        vm.requestAnotherSpot()

        assertEquals(listOf(RESERVATION_ID), world.reservations.reassigned)
        assertEquals(CheckInMessage.SPOT_REASSIGNED, vm.state.value.message)
    }

    @Test
    fun siNoQuedaOtroLugarLoAvisaSinRomperNada() = runTest {
        val world = World()
        world.reservations.reassignResult = ReservationOutcome.Rejected(ReservationRejection.NO_SPOTS)
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.spots.emit(availability(SpotStatus.OCCUPIED))

        vm.requestAnotherSpot()

        assertEquals(CheckInMessage.NO_OTHER_SPOT, vm.state.value.message)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun conElLugarLibreNoSePideOtroNiSeLlamaAlBackend() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)
        world.spots.emit(availability(SpotStatus.FREE))

        vm.requestAnotherSpot()

        assertEquals(emptyList<String>(), world.reservations.reassigned)
        assertEquals(CheckInMessage.UNKNOWN_ERROR, vm.state.value.message)
    }

    @Test
    fun elCheckOutMuestraElResumenYLiberaLaReserva() = runTest {
        val world = World()
        // El resumen lo calcula el backend; acá se fija uno para comprobar que llega a la pantalla.
        world.reservations.checkOutResult =
            ReservationOutcome.Success(CheckOutSummary(stayMinutes = 45, amount = 750))
        world.reservations.emit(listOf(reservation(status = ReservationStatus.IN_USE)))
        val vm = world.viewModel()
        collecting(vm)
        vm.load(RESERVATION_ID)

        vm.checkOut()

        assertEquals(listOf(RESERVATION_ID), world.reservations.checkedOut)
        assertEquals(45, vm.state.value.summary?.stayMinutes)
        assertEquals(750, vm.state.value.summary?.amount)
    }

    /** Todo lo que el ViewModel necesita alrededor, con una hora fija. */
    private class World(val now: Instant = Instant.parse("2026-10-18T14:00:00Z")) {
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val reservations = FakeReservationRepository(listOf(reservation()))
        val parkingLots = FakeParkingLotRepository(listOf(LOT))
        val spots = FakeSpotAvailabilityRepository(availability(SpotStatus.RESERVED))
        val location = FakeLocationRepository()
        val geofences = FakeGeofenceRepository()
        val reminders = FakeReminderScheduler()
        val notifications = InMemoryNotificationRepository()

        fun viewModel() = CheckInViewModel(
            reservations = reservations,
            parkingLots = parkingLots,
            spots = spots,
            location = location,
            checkInUseCase = CheckInUseCase(reservations, clock),
            checkOutUseCase = CheckOutUseCase(reservations),
            reassignSpotUseCase = ReassignSpotUseCase(reservations, clock),
            syncAlerts = SyncReservationAlertsUseCase(
                reservations = reservations,
                parkingLots = parkingLots,
                geofences = geofences,
                reminders = reminders,
                notifications = notifications,
                messages = FakeAlertMessages,
                clock = clock,
            ),
            clock = clock,
        )
    }

    private companion object {
        const val RESERVATION_ID = "res-1"
        val LOT_LOCATION = Coordinates(-34.5984, -58.4200)

        val LOT = ParkingLot(
            id = "utn-medrano",
            name = "UTN Medrano",
            address = "Medrano 951",
            location = LOT_LOCATION,
            toleranceMinutes = 15,
            geofenceRadiusMeters = 300,
            hourlyRate = 1_000,
        )

        fun reservation(status: ReservationStatus = ReservationStatus.CONFIRMED) = Reservation(
            id = RESERVATION_ID,
            userId = "user-1",
            parkingLotId = LOT.id,
            spotId = "spot-07",
            spotCode = "07",
            spotType = SpotType.STANDARD,
            arrivesAt = Instant.parse("2026-10-18T14:00:00Z"),
            durationMinutes = 60,
            endsAt = Instant.parse("2026-10-18T15:00:00Z"),
            toleranceUntil = Instant.parse("2026-10-18T14:15:00Z"),
            status = status,
        )

        fun availability(status: SpotStatus) = SpotAvailability(
            spots = listOf(Spot("spot-07", "07", SpotType.STANDARD, status)),
            isFromCache = false,
            lastUpdate = Instant.parse("2026-10-18T14:00:00Z"),
        )
    }
}
