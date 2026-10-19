package com.sensorpark.feature.map

import com.sensorpark.core.testing.FakeLocationRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.LocationReading
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.SearchNearbyParkingLotsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyParkingLotsViewModelTest {

    /** Tres estacionamientos: uno en Medrano, uno a ~1 km y uno lejos, en Belgrano. */
    private val abasto = Samples.UTN_MEDRANO.copy(
        id = "abasto",
        name = "Playa Abasto",
        location = Coordinates(-34.6037, -58.4110),
        freeSpots = 0,
        freeSpotsByType = mapOf(SpotType.STANDARD to 0),
    )
    private val belgrano = Samples.UTN_MEDRANO.copy(
        id = "belgrano",
        name = "Playa Belgrano",
        location = Coordinates(-34.5627, -58.4563),
    )
    private val parkingLots = FakeParkingLotRepository(listOf(Samples.UTN_MEDRANO, abasto, belgrano))

    /** Una ubicación en Belgrano, lejos de la zona por defecto. */
    private val inBelgrano = LocationReading(Coordinates(-34.5630, -58.4560), accuracyMeters = 30f)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collecting(vm: NearbyParkingLotsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.result.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.selected.collect() }
    }

    private fun viewModel(location: FakeLocationRepository) =
        NearbyParkingLotsViewModel(SearchNearbyParkingLotsUseCase(parkingLots), location)

    private fun NearbyParkingLotsViewModel.ids(): List<String> =
        (result.value as DataResult.Success<List<NearbyParkingLot>>).value.map { it.parkingLot.id }

    @Test
    fun conElPermisoYaDadoBuscaDesdeLaUbicacionDelTelefono() = runTest {
        val location = FakeLocationRepository(reading = inBelgrano)
        val vm = viewModel(location)
        collecting(vm)

        assertEquals(LocationAccess.GRANTED, vm.access.value)
        assertTrue(vm.origin.value.fromUser)
        assertEquals(inBelgrano.coordinates, vm.origin.value.coordinates)
        assertEquals(listOf("belgrano"), vm.ids())
    }

    @Test
    fun sinPermisoTodaviaBuscaDesdeLaZonaPorDefectoYNoPideUbicacion() = runTest {
        val location = FakeLocationRepository(permission = false, reading = null)
        val vm = viewModel(location)
        collecting(vm)

        assertEquals(LocationAccess.UNKNOWN, vm.access.value)
        assertFalse(vm.origin.value.fromUser)
        assertEquals(NearbyParkingLotsViewModel.DEFAULT_ORIGIN, vm.origin.value.coordinates)
        assertEquals(0, location.requests)
        // Medrano y Abasto están a menos de 2 km de la zona por defecto; Belgrano no.
        assertEquals(listOf("utn-medrano", "abasto"), vm.ids())
    }

    @Test
    fun alDarElPermisoBuscaDesdeLaUbicacion() = runTest {
        val location = FakeLocationRepository(permission = false, reading = null)
        val vm = viewModel(location)
        collecting(vm)

        location.grantPermission(at = inBelgrano)
        vm.onPermissionResult(granted = true, canAskAgain = true)

        assertEquals(LocationAccess.GRANTED, vm.access.value)
        assertEquals(listOf("belgrano"), vm.ids())
    }

    @Test
    fun alNegarloSeQuedaEnLaZonaPorDefectoYSePuedeVolverAPedir() = runTest {
        val location = FakeLocationRepository(permission = false, reading = null)
        val vm = viewModel(location)
        collecting(vm)

        vm.onPermissionResult(granted = false, canAskAgain = true)

        assertEquals(LocationAccess.DENIED, vm.access.value)
        assertFalse(vm.origin.value.fromUser)
        assertEquals(listOf("utn-medrano", "abasto"), vm.ids())
    }

    @Test
    fun alNegarloParaSiempreLoDistingue() = runTest {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))
        collecting(vm)

        vm.onPermissionResult(granted = false, canAskAgain = false)

        assertEquals(LocationAccess.DENIED_FOREVER, vm.access.value)
    }

    @Test
    fun sinPermisoSePuedeMoverElMapaYBuscarEnOtraZona() = runTest {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))
        collecting(vm)
        vm.onPermissionResult(granted = false, canAskAgain = true)

        vm.searchHere(belgrano.location)

        assertEquals(belgrano.location, vm.origin.value.coordinates)
        assertFalse(vm.origin.value.fromUser)
        assertEquals(listOf("belgrano"), vm.ids())
    }

    @Test
    fun siLaUbicacionNoLlegaSeQuedaDondeEstaba() = runTest {
        // Con permiso pero con la ubicación del teléfono apagada.
        val vm = viewModel(FakeLocationRepository(permission = true, reading = null))
        collecting(vm)

        assertFalse(vm.origin.value.fromUser)
        assertFalse(vm.locating.value)
        assertEquals(listOf("utn-medrano", "abasto"), vm.ids())
    }

    @Test
    fun losFiltrosCambianLoQueSeMuestra() = runTest {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))
        collecting(vm)

        vm.toggleOnlyWithFreeSpots()

        // Abasto está completo.
        assertEquals(listOf("utn-medrano"), vm.ids())
    }

    @Test
    fun tocarUnMarcadorLoSeleccionaYUnFiltroQueLoSacaLoDeselecciona() = runTest {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))
        collecting(vm)

        vm.select("abasto")
        assertEquals("abasto", vm.selected.value?.parkingLot?.id)

        vm.toggleOnlyWithFreeSpots()
        assertNull(vm.selected.value)
    }

    @Test
    fun seAlternaEntreMapaYLista() = runTest {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))

        assertEquals(ViewMode.MAP, vm.mode.value)
        vm.toggleMode()
        assertEquals(ViewMode.LIST, vm.mode.value)
        vm.toggleMode()
        assertEquals(ViewMode.MAP, vm.mode.value)
    }

    @Test
    fun comoLlegarArmaElLinkConPuntoDecimalAunqueElTelefonoEsteEnEspanol() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-AR"))
        try {
            assertEquals(
                "https://www.google.com/maps/dir/?api=1&destination=-34.598400,-58.420000&travelmode=driving",
                directionsUrl(Samples.UTN_MEDRANO.location),
            )
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
