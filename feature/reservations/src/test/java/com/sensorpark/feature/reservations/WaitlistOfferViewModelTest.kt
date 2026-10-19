package com.sensorpark.feature.reservations

import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.usecase.AcceptWaitlistOfferUseCase
import com.sensorpark.domain.usecase.DeclineWaitlistOfferUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WaitlistOfferViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collecting(vm: WaitlistOfferViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

    @Test
    fun muestraLaOfertaConElTiempoQueLeQueda() = runTest {
        val world = World(Samples.offeredEntry(spotCode = "12"))
        val vm = world.viewModel()
        collecting(vm)
        vm.load("w-1")

        val state = vm.state.value
        assertEquals("12", state.entry?.offer?.spotCode)
        assertEquals("UTN Medrano", state.parkingLotName)
        assertEquals(Duration.ofMinutes(5), state.timeLeft(Samples.NOW))
        assertEquals(Duration.ofMinutes(2), state.timeLeft(Samples.NOW.plus(Duration.ofMinutes(3))))
        assertEquals(Duration.ZERO, state.timeLeft(Samples.NOW.plus(Duration.ofMinutes(6))))
    }

    @Test
    fun aceptarLaConvierteEnUnaReservaConfirmada() = runTest {
        val world = World(Samples.offeredEntry(spotCode = "12"))
        val vm = world.viewModel()
        collecting(vm)
        vm.load("w-1")

        vm.accept()

        assertEquals(listOf("w-1"), world.reservations.accepted)
        assertEquals("12", vm.state.value.confirmation?.spotCode)
        val reservation = world.reservations.current.single()
        assertEquals(ReservationStatus.CONFIRMED, reservation.status)
        assertEquals(WaitlistStatus.ACCEPTED, world.reservations.waitlistEntries.single().status)

        // Ya contestó: otro toque no vuelve a llamar al backend.
        vm.accept()
        vm.decline()
        assertEquals(listOf("w-1"), world.reservations.accepted)
        assertTrue(world.reservations.declined.isEmpty())
    }

    @Test
    fun rechazarSeLaPasaAlSiguiente() = runTest {
        val world = World(Samples.offeredEntry())
        val vm = world.viewModel()
        collecting(vm)
        vm.load("w-1")

        vm.decline()

        assertEquals(listOf("w-1"), world.reservations.declined)
        assertTrue(vm.state.value.declined)
        assertEquals(WaitlistStatus.DECLINED, vm.state.value.entry?.status)
    }

    @Test
    fun siElBackendDiceQueVencioSeMuestraElMotivo() = runTest {
        val world = World(Samples.offeredEntry())
        world.reservations.acceptResult = ReservationOutcome.Rejected(ReservationRejection.OFFER_EXPIRED)
        val vm = world.viewModel()
        collecting(vm)
        vm.load("w-1")

        vm.accept()

        assertEquals(ReservationRejection.OFFER_EXPIRED, vm.state.value.rejection)
        assertNull(vm.state.value.confirmation)
    }

    @Test
    fun laOfertaLlegaEnVivoMientrasLaPantallaEstaAbierta() = runTest {
        val world = World(Samples.waitlistEntry())
        val vm = world.viewModel()
        collecting(vm)
        vm.load("w-1")
        assertEquals(WaitlistStatus.WAITING, vm.state.value.entry?.status)

        world.reservations.offer("w-1", spotCode = "03")

        assertEquals(WaitlistStatus.OFFERED, vm.state.value.entry?.status)
        assertEquals("03", vm.state.value.entry?.offer?.spotCode)
    }

    @Test
    fun unaEntradaQueNoEsDelUsuarioNoSeEncuentra() = runTest {
        val world = World(Samples.offeredEntry())
        val vm = world.viewModel()
        collecting(vm)
        vm.load("otra")

        assertNull(vm.state.value.entry)
        vm.accept()
        assertTrue(world.reservations.accepted.isEmpty())
    }

    private class World(vararg entries: com.sensorpark.domain.model.WaitlistEntry) {
        val clock: Clock = Clock.fixed(Samples.NOW, ZoneOffset.UTC)
        val reservations = FakeReservationRepository(waitlist = entries.toList(), clock = { Samples.NOW })
        val parkingLots = FakeParkingLotRepository()

        fun viewModel() = WaitlistOfferViewModel(
            reservations = reservations,
            parkingLots = parkingLots,
            acceptOffer = AcceptWaitlistOfferUseCase(reservations, clock),
            declineOffer = DeclineWaitlistOfferUseCase(reservations),
            clock = clock,
        )
    }
}
