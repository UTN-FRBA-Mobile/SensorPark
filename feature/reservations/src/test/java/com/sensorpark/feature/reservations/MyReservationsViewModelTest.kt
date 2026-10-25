package com.sensorpark.feature.reservations

import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeStayHistoryRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.Stay
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.usecase.CancelReservationUseCase
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MyReservationsViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collecting(vm: MyReservationsViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

    @Test
    fun separaLaActivaDeLasProximasYDelHistorial() = runTest {
        val world = World()
        world.reservations.emit(
            listOf(
                reservation("ahora", arrivesAt = NOW.plus(Duration.ofMinutes(10))),
                reservation("manana", arrivesAt = NOW.plus(Duration.ofDays(1))),
                reservation("vieja", arrivesAt = NOW.minus(Duration.ofDays(2)), status = ReservationStatus.FINISHED),
            ),
        )
        val vm = world.viewModel()
        collecting(vm)

        assertEquals("ahora", vm.state.value.active?.reservation?.id)
        assertEquals(listOf("manana"), vm.state.value.upcoming.map { it.reservation.id })
        assertEquals(listOf("vieja"), vm.state.value.history.map { it.reservation.id })
    }

    @Test
    fun unaReservaLejanaTodaviaNoEsLaDeAhora() = runTest {
        val world = World()
        world.reservations.emit(listOf(reservation("manana", arrivesAt = NOW.plus(Duration.ofDays(1)))))
        val vm = world.viewModel()
        collecting(vm)

        assertNull(vm.state.value.active)
        assertEquals(listOf("manana"), vm.state.value.upcoming.map { it.reservation.id })
    }

    @Test
    fun laQueEstaEnUsoEsLaActivaAunqueHayaEmpezadoHaceRato() = runTest {
        val world = World()
        world.reservations.emit(
            listOf(
                reservation(
                    "en-uso",
                    arrivesAt = NOW.minus(Duration.ofHours(5)),
                    status = ReservationStatus.IN_USE,
                ),
            ),
        )
        val vm = world.viewModel()
        collecting(vm)

        assertEquals("en-uso", vm.state.value.active?.reservation?.id)
    }

    @Test
    fun muestraElNombreDelEstacionamientoDeCadaReserva() = runTest {
        val world = World()
        world.reservations.emit(listOf(reservation("ahora", arrivesAt = NOW.plus(Duration.ofMinutes(10)))))
        val vm = world.viewModel()
        collecting(vm)

        assertEquals("UTN Medrano", vm.state.value.active?.parkingLotName)
    }

    @Test
    fun laCuentaRegresivaDeToleranciaNuncaEsNegativa() = runTest {
        val world = World()
        world.reservations.emit(listOf(reservation("ahora", arrivesAt = NOW.minus(Duration.ofHours(3)))))
        val vm = world.viewModel()
        collecting(vm)

        val card = vm.state.value.active ?: vm.state.value.upcoming.first()
        assertTrue(card.toleranceLeft(NOW).isZero)
    }

    @Test
    fun cancelarLlamaAlBackend() = runTest {
        val world = World()
        val reservation = reservation("ahora", arrivesAt = NOW.plus(Duration.ofMinutes(10)))
        world.reservations.emit(listOf(reservation))
        val vm = world.viewModel()
        collecting(vm)

        vm.cancel(reservation)

        assertEquals(listOf("ahora"), world.reservations.cancelled)
        assertNull(vm.state.value.rejection)
    }

    @Test
    fun cancelarUnaReservaEnUsoLoRechazaSinLlamarAlBackend() = runTest {
        val world = World()
        val reservation = reservation(
            "en-uso",
            arrivesAt = NOW.minus(Duration.ofMinutes(30)),
            status = ReservationStatus.IN_USE,
        )
        world.reservations.emit(listOf(reservation))
        val vm = world.viewModel()
        collecting(vm)

        vm.cancel(reservation)

        assertEquals(emptyList<String>(), world.reservations.cancelled)
        assertEquals(ReservationRejection.CANCEL_NOT_ALLOWED, vm.state.value.rejection)
    }

    @Test
    fun sinPoderLeerLasReservasMuestraLasEstadiasGuardadasEnElTelefono() = runTest {
        val world = World()
        val stay = Stay(
            reservationId = "vieja",
            parkingLotId = LOT.id,
            parkingLotName = LOT.name,
            spotCode = "07",
            arrivesAt = NOW.minus(Duration.ofDays(2)),
            checkedOutAt = NOW.minus(Duration.ofDays(2)).plus(Duration.ofMinutes(65)),
            stayMinutes = 65,
            amount = 1_083,
        )
        world.stays.emit(listOf(stay))
        world.reservations.fail(DataErrorReason.NO_CONNECTION)
        val vm = world.viewModel()
        collecting(vm)

        assertEquals(DataErrorReason.NO_CONNECTION, vm.state.value.failure)
        assertEquals(listOf(stay), vm.state.value.savedStays)
    }

    @Test
    fun unErrorDeLaBaseSeMuestraSinRomperLaPantalla() = runTest {
        val world = World()
        world.reservations.fail(DataErrorReason.PERMISSION_DENIED)
        val vm = world.viewModel()
        collecting(vm)

        assertEquals(DataErrorReason.PERMISSION_DENIED, vm.state.value.failure)
    }

    @Test
    fun muestraLasListasDeEsperaAbiertasConSuOfertaYDejaSalir() = runTest {
        val world = World()
        world.reservations.emitWaitlist(
            listOf(
                Samples.waitlistEntry("esperando", position = 2, arrivesAt = NOW.plus(Duration.ofHours(3))),
                Samples.waitlistEntry(
                    "con-oferta",
                    status = WaitlistStatus.OFFERED,
                    arrivesAt = NOW.plus(Duration.ofHours(2)),
                    offerExpiresAt = NOW.plus(Duration.ofMinutes(3)),
                ),
                Samples.waitlistEntry("salio", status = WaitlistStatus.LEFT),
            ),
        )
        val vm = world.viewModel()
        collecting(vm)

        // Sólo las abiertas, por hora de llegada, con el nombre del estacionamiento.
        val cards = vm.state.value.waitlist
        assertEquals(listOf("con-oferta", "esperando"), cards.map { it.entry.id })
        assertEquals("UTN Medrano", cards.first().parkingLotName)
        assertEquals(Duration.ofMinutes(3), cards.first().offerTimeLeft(NOW))
        assertEquals(Duration.ZERO, cards.last().offerTimeLeft(NOW))
        assertFalse("con una lista de espera no está vacía", vm.state.value.isEmpty)

        vm.leaveWaitlist(cards.last().entry)

        assertEquals(listOf("esperando"), world.reservations.left)
        assertEquals(listOf("con-oferta"), vm.state.value.waitlist.map { it.entry.id })
    }

    private class World {
        val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val reservations = FakeReservationRepository()
        val parkingLots = FakeParkingLotRepository(listOf(LOT))
        val stays = FakeStayHistoryRepository()

        fun viewModel() = MyReservationsViewModel(
            reservations = reservations,
            parkingLots = parkingLots,
            cancelReservation = CancelReservationUseCase(reservations),
            stayHistory = stays,
            clock = clock,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-18T14:00:00Z")

        val LOT = ParkingLot(
            id = "utn-medrano",
            name = "UTN Medrano",
            address = "Medrano 951",
            location = Coordinates(-34.5984, -58.4200),
            toleranceMinutes = 15,
            geofenceRadiusMeters = 300,
            hourlyRate = 1_000,
        )

        fun reservation(
            id: String,
            arrivesAt: Instant,
            status: ReservationStatus = ReservationStatus.CONFIRMED,
        ) = Reservation(
            id = id,
            userId = "user-1",
            parkingLotId = LOT.id,
            spotId = "spot-07",
            spotCode = "07",
            spotType = SpotType.STANDARD,
            arrivesAt = arrivesAt,
            durationMinutes = 60,
            endsAt = arrivesAt.plus(Duration.ofHours(1)),
            toleranceUntil = arrivesAt.plus(Duration.ofMinutes(15)),
            status = status,
        )
    }
}
