package com.sensorpark.feature.reservations

import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeUserRepository
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.Schedule
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.CreateReservationUseCase
import com.sensorpark.domain.usecase.JoinWaitlistUseCase
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
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReserveViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collecting(vm: ReserveViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

    @Test
    fun reservaConLaFranjaYElTipoDeLugarElegidos() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        vm.setTime(LocalTime.of(15, 30))
        vm.setDuration(120)
        vm.confirm()

        val request = world.reservations.created.single()
        assertEquals(LOT.id, request.parkingLotId)
        assertEquals(120, request.durationMinutes)
        assertEquals(SpotType.STANDARD, request.spotType)
        // 15:30 de Buenos Aires son las 18:30 UTC.
        assertEquals(Instant.parse("2026-10-18T18:30:00Z"), request.arrivesAt)
        assertEquals("07", vm.state.value.confirmation?.spotCode)
    }

    @Test
    fun reservarUnLugarDelPlanoPideEseLugarYSuTipo() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id, PickedSpot(id = "spot-11", code = "11", type = SpotType.MOTORCYCLE))

        assertEquals("11", vm.state.value.picked?.code)
        assertEquals(SpotType.MOTORCYCLE, vm.state.value.spotType)
        vm.confirm()

        val request = world.reservations.created.single()
        assertEquals("spot-11", request.spotId)
        assertEquals(SpotType.MOTORCYCLE, request.spotType)
    }

    @Test
    fun elegirUnTipoDespuesDeUnLugarDejaDePedirEseLugar() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id, PickedSpot(id = "spot-11", code = "11", type = SpotType.MOTORCYCLE))

        vm.setSpotType(SpotType.STANDARD)
        vm.confirm()

        assertNull(vm.state.value.picked)
        val request = world.reservations.created.single()
        assertNull(request.spotId)
        assertEquals(SpotType.STANDARD, request.spotType)
    }

    @Test
    fun sinLugarElegidoSePideSoloElTipo() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        vm.confirm()

        assertNull(world.reservations.created.single().spotId)
    }

    @Test
    fun laTarifaEstimadaSaleDeLaTarifaPorHora() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        vm.setDuration(120)

        // 1000 por hora, dos horas.
        assertEquals(2_000, vm.state.value.estimatedAmount)
    }

    @Test
    fun sinLugarOfreceAlternativasEnVezDeUnErrorSeco() = runTest {
        val world = World()
        world.reservations.createResult = ReservationOutcome.Rejected(ReservationRejection.NO_SPOTS)
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)
        vm.setDuration(120)

        vm.confirm()

        assertEquals(ReservationRejection.NO_SPOTS, vm.state.value.rejection)
        val alternatives = vm.state.value.alternatives
        assertTrue(alternatives.any { it is ReserveAlternative.ShorterDuration })
        assertTrue(alternatives.contains(ReserveAlternative.AnotherTime))
        assertNull(vm.state.value.confirmation)
    }

    @Test
    fun noDejaConfirmarDosVecesLaMismaReserva() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        vm.confirm()
        vm.confirm()

        assertEquals(1, world.reservations.created.size)
    }

    @Test
    fun sinPrioridadDeclaradaNoSeOfrecenLugaresPreferenciales() = runTest {
        val world = World()
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        assertFalse(vm.state.value.spotTypes.contains(SpotType.PRIORITY))
    }

    @Test
    fun conPrioridadDeclaradaSeOfrecenLosPreferenciales() = runTest {
        val world = World(category = Category.REDUCED_MOBILITY)
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        assertTrue(vm.state.value.spotTypes.contains(SpotType.PRIORITY))
    }

    @Test
    fun elRechazoDelBackendSePuedeDescartar() = runTest {
        val world = World()
        world.reservations.createResult = ReservationOutcome.Rejected(ReservationRejection.LIMIT_REACHED)
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)
        vm.confirm()
        assertEquals(ReservationRejection.LIMIT_REACHED, vm.state.value.rejection)

        vm.dismissError()

        assertNull(vm.state.value.rejection)
    }

    @Test
    fun sinLugarSePuedeAnotarEnLaListaDeEsperaConElMismoPedido() = runTest {
        val world = World()
        world.reservations.createResult = ReservationOutcome.Rejected(ReservationRejection.NO_SPOTS)
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        vm.setTime(LocalTime.of(15, 30))
        vm.confirm()
        assertTrue(vm.state.value.canJoinWaitlist)

        vm.joinWaitlist()

        val request = world.reservations.joined.single()
        assertEquals(world.reservations.created.single(), request)
        assertEquals(1, vm.state.value.waitlist?.position)
        // Ya anotado: el error se va y no se ofrece anotarse de nuevo.
        assertNull(vm.state.value.rejection)
        assertFalse(vm.state.value.canJoinWaitlist)
    }

    @Test
    fun conOtroRechazoNoSeOfreceLaListaDeEspera() = runTest {
        val world = World()
        world.reservations.createResult = ReservationOutcome.Rejected(ReservationRejection.LIMIT_REACHED)
        val vm = world.viewModel()
        collecting(vm)
        vm.load(LOT.id)

        vm.confirm()

        assertFalse(vm.state.value.canJoinWaitlist)
    }

    private class World(category: Category = Category.STANDARD) {
        // 14:00Z es el mediodía de Buenos Aires: la franja por defecto cae dentro del horario.
        val clock: Clock = Clock.fixed(Instant.parse("2026-10-18T14:00:00Z"), ZoneOffset.UTC)
        val reservations = FakeReservationRepository()
        val parkingLots = FakeParkingLotRepository(listOf(LOT))
        val users = FakeUserRepository(category)

        fun viewModel() = ReserveViewModel(
            parkingLots = parkingLots,
            users = users,
            createReservation = CreateReservationUseCase(reservations, clock),
            enterWaitlist = JoinWaitlistUseCase(reservations, clock),
            clock = clock,
        )
    }

    private companion object {
        val LOT = ParkingLot(
            id = "utn-medrano",
            name = "UTN Medrano",
            address = "Medrano 951",
            location = Coordinates(-34.5984, -58.4200),
            schedule = Schedule(LocalTime.of(0, 0), LocalTime.of(23, 59)),
            toleranceMinutes = 15,
            geofenceRadiusMeters = 300,
            hourlyRate = 1_000,
            capacity = 14,
            freeSpots = 9,
            capacityByType = mapOf(SpotType.STANDARD to 10),
            freeSpotsByType = mapOf(SpotType.STANDARD to 7),
        )
    }
}
