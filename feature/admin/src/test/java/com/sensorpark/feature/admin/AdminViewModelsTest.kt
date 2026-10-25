package com.sensorpark.feature.admin

import com.sensorpark.core.testing.FakeParkingLotAdminRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.domain.model.ParkingLotField
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelsTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val admin = FakeParkingLotAdminRepository()
    private val parkingLots = FakeParkingLotRepository()

    @Test
    fun elFormularioNoGuardaConErroresYLosMuestraRecienAlIntentar() = runTest {
        val vm = ParkingLotFormViewModel(admin, parkingLots)
        assertTrue("al abrir no hay errores a la vista", vm.state.value.problems.isEmpty())

        vm.save()

        assertTrue(admin.created.isEmpty())
        assertTrue(ParkingLotField.NAME in vm.state.value.problems)
        assertTrue(ParkingLotField.LOCATION in vm.state.value.problems)
    }

    @Test
    fun crearConDatosValidosDevuelveElIdParaIrAlPanel() = runTest {
        val vm = ParkingLotFormViewModel(admin, parkingLots)
        vm.edit {
            it.copy(name = "Playa Nueva", address = "Calle 1", latitude = "-34,6", longitude = "-58,4", opensAt = "7:00", closesAt = "22:00")
        }

        vm.save()

        val draft = admin.created.single()
        assertEquals(-34.6, draft.latitude!!, 0.0)
        assertEquals(LocalTime.of(7, 0), draft.opensAt)
        assertEquals("lot-nuevo", vm.state.value.savedId)
    }

    @Test
    fun unaHoraMalEscritaEsUnErrorDeHorario() = runTest {
        val vm = ParkingLotFormViewModel(admin, parkingLots)
        vm.edit { it.copy(name = "X", address = "Y", latitude = "-34.6", longitude = "-58.4", opensAt = "25:99", closesAt = "") }
        vm.save()
        assertEquals(setOf(ParkingLotField.SCHEDULE), vm.state.value.problems)
    }

    @Test
    fun editarCargaLosDatosDelEstacionamientoYGuardaLosCambios() = runTest {
        val vm = ParkingLotFormViewModel(admin, parkingLots)
        vm.load(Samples.UTN_MEDRANO.id)
        assertEquals(Samples.UTN_MEDRANO.name, vm.state.value.input.name)

        vm.edit { it.copy(hourlyRate = "3000") }
        vm.save()

        val (id, draft) = admin.updated.single()
        assertEquals(Samples.UTN_MEDRANO.id, id)
        assertEquals(3000, draft.hourlyRate)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun elPanelSacaYDevuelveLugaresYMuestraPorQueNoPudo() = runTest {
        val spots = FakeSpotAvailabilityRepository(
            Samples.availability(Samples.spot("01"), Samples.spot("02", status = SpotStatus.OUT_OF_SERVICE)),
        )
        val vm = panel(spots)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.spots.collect() }
        vm.load(Samples.UTN_MEDRANO.id)
        val (free, broken) = vm.spots.value!!

        vm.toggleService(free)
        vm.toggleService(broken)
        assertEquals(listOf("spot-01" to false, "spot-02" to true), admin.serviceChanges)

        admin.spotResult = ReservationOutcome.Rejected(ReservationRejection.SPOT_HAS_RESERVATION)
        vm.delete(free)
        assertEquals(ReservationRejection.SPOT_HAS_RESERVATION, vm.action.value.rejection)
        vm.dismissError()
        assertNull(vm.action.value.rejection)
    }

    @Test
    fun guardarUnLugarCierraElDialogo() = runTest {
        val vm = panel(FakeSpotAvailabilityRepository())
        vm.load(Samples.UTN_MEDRANO.id)
        var closed = false

        vm.saveSpot(SpotDraft(code = "15")) { closed = true }

        assertEquals(listOf(SpotDraft(code = "15")), admin.savedSpots)
        assertTrue(closed)
    }

    @Test
    fun elResumenDelDiaCuentaSoloLasReservasDeHoy() = runTest {
        val vm = panel(FakeSpotAvailabilityRepository(Samples.availability(Samples.spot("01"))))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.summary.collect() }
        vm.load(Samples.UTN_MEDRANO.id)
        admin.emitReservations(
            listOf(
                Samples.reservation("hoy"),
                Samples.reservation("hoy-cancelada", status = ReservationStatus.CANCELLED),
                Samples.reservation("manana", arrivesAt = Samples.NOW.plus(Duration.ofDays(1))),
            ),
        )

        val summary = vm.summary.value
        assertEquals(1, summary.free)
        assertEquals(2, summary.reservations)
        assertEquals(1, summary.cancelled)
    }

    private fun panel(spots: FakeSpotAvailabilityRepository) = ParkingLotPanelViewModel(
        repository = parkingLots,
        spotsRepository = spots,
        admin = admin,
        clock = Clock.fixed(Samples.NOW, ZoneOffset.UTC),
    )
}
