package com.sensorpark.feature.map

import com.sensorpark.core.testing.FakeFavoritesRepository
import com.sensorpark.core.testing.FakeLocationRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.LocationReading
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyParkingLotsViewModelTest {
    // Tres estacionamientos sobre la misma avenida: a unos 0, 1,2 y 4,5 km de UTN Medrano.
    private val medrano = Samples.UTN_MEDRANO.copy(id = "medrano", name = "UTN Medrano", location = Coordinates(-34.5984, -58.4200))
    private val abasto = Samples.UTN_MEDRANO.copy(
        id = "abasto", name = "Playa Abasto", location = Coordinates(-34.6037, -58.4108), freeSpots = 0,
    )
    private val lejos = Samples.UTN_MEDRANO.copy(id = "lejos", name = "Lejos", location = Coordinates(-34.6400, -58.3500))
    private val lots = listOf(lejos, abasto, medrano)

    private val parkingLots = FakeParkingLotRepository(lots)
    private val favorites = FakeFavoritesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(location: FakeLocationRepository = FakeLocationRepository()) =
        NearbyParkingLotsViewModel(parkingLots, location, favorites, UnconfinedTestDispatcher())

    /** Como la pantalla: recolecta el resultado desde el principio. */
    private fun TestScope.collecting(vm: NearbyParkingLotsViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.result.collect {} }

    private fun NearbyParkingLotsViewModel.ids(): List<String> =
        (result.value as DataResult.Success).value.map { it.parkingLot.id }

    @Test
    fun conPermisoBuscaDesdeLaUbicacionDelTelefonoYOrdenaPorDistancia() = runTest {
        val phone = FakeLocationRepository(LocationReading(Coordinates(-34.6037, -58.4108), accuracyMeters = 20f))
        val vm = viewModel(phone)
        collecting(vm)
        advanceTimeBy(1)

        assertEquals(OriginSource.DEVICE, vm.origin.value.source)
        assertEquals(Coordinates(-34.6037, -58.4108), vm.origin.value.coordinates)
        assertEquals(listOf("abasto", "medrano"), vm.ids())
        assertEquals(LocationAccess.GRANTED, vm.access.value)
    }

    @Test
    fun sinPermisoNoQuedaEnBlancoYBuscaEnLaZonaPorDefecto() = runTest {
        val phone = FakeLocationRepository().also { it.denyPermission() }
        val vm = viewModel(phone)
        collecting(vm)

        assertEquals(OriginSource.DEFAULT_ZONE, vm.origin.value.source)
        assertEquals(LocationAccess.UNKNOWN, vm.access.value)
        assertEquals(listOf("medrano", "abasto"), vm.ids())
        assertEquals(0, phone.approximateCalls)
    }

    @Test
    fun alRechazarElPermisoSeQuedaEnLaZonaPorDefecto() = runTest {
        val phone = FakeLocationRepository().also { it.denyPermission() }
        val vm = viewModel(phone)
        collecting(vm)

        vm.onPermissionResult(granted = false)

        assertEquals(LocationAccess.DENIED, vm.access.value)
        assertEquals(OriginSource.DEFAULT_ZONE, vm.origin.value.source)
    }

    @Test
    fun alConcederElPermisoLeeLaUbicacionYBuscaDesdeAhi() = runTest {
        val phone = FakeLocationRepository(LocationReading(Coordinates(-34.6400, -58.3500), accuracyMeters = 20f))
            .also { it.denyPermission() }
        val vm = viewModel(phone)
        collecting(vm)
        assertEquals(listOf("medrano", "abasto"), vm.ids())

        phone.grantPermission()
        phone.reading = LocationReading(Coordinates(-34.6400, -58.3500), accuracyMeters = 20f)
        vm.onPermissionResult(granted = true)

        assertEquals(OriginSource.DEVICE, vm.origin.value.source)
        assertEquals(listOf("lejos"), vm.ids())
    }

    @Test
    fun conPermisoPeroSinLecturaAvisaYSigueEnLaZonaPorDefecto() = runTest {
        val vm = viewModel(FakeLocationRepository(reading = null))
        collecting(vm)

        assertTrue(vm.locationFailed.value)
        assertFalse(vm.locating.value)
        assertEquals(OriginSource.DEFAULT_ZONE, vm.origin.value.source)
    }

    @Test
    fun buscarEnEstaZonaCambiaElOrigenYDeseleccionaElMarcador() = runTest {
        val vm = viewModel(FakeLocationRepository().also { it.denyPermission() })
        collecting(vm)
        vm.select("medrano")

        vm.searchHere(Coordinates(-34.6400, -58.3500))

        assertEquals(OriginSource.MAP_AREA, vm.origin.value.source)
        assertEquals(null, vm.selectedId.value)
        assertEquals(listOf("lejos"), vm.ids())
    }

    @Test
    fun alQuitarElPermisoEnAjustesVuelveALaZonaPorDefecto() = runTest {
        val phone = FakeLocationRepository(LocationReading(Coordinates(-34.6400, -58.3500), accuracyMeters = 20f))
        val vm = viewModel(phone)
        collecting(vm)
        assertEquals(OriginSource.DEVICE, vm.origin.value.source)

        phone.denyPermission()
        vm.onResume()

        assertEquals(LocationAccess.DENIED, vm.access.value)
        assertEquals(OriginSource.DEFAULT_ZONE, vm.origin.value.source)
    }

    @Test
    fun losFiltrosCambianLaListaSinVolverALeerLosEstacionamientos() = runTest {
        val vm = viewModel()
        collecting(vm)
        assertEquals(listOf("medrano", "abasto"), vm.ids())

        vm.toggleOnlyWithFreeSpots()
        assertEquals(listOf("medrano"), vm.ids())

        vm.toggleOnlyWithFreeSpots()
        vm.selectSpotType(SpotType.MOTORCYCLE)
        assertEquals(listOf("medrano", "abasto"), vm.ids())

        // Elegir el mismo tipo de nuevo lo saca.
        vm.selectSpotType(SpotType.MOTORCYCLE)
        assertEquals(null, vm.filter.value.spotType)
    }

    @Test
    fun soloFavoritosMuestraLosElegidosAunqueEstenLejos() = runTest {
        favorites.setFavorite("lejos", true)
        val vm = viewModel()
        collecting(vm)
        collectFavorites(vm)

        vm.toggleFavoritesOnly()

        assertEquals(listOf("lejos"), vm.ids())
        assertTrue(vm.favoritesOnly.value)
    }

    @Test
    fun tocarElCorazonMarcaYDesmarcaElFavorito() = runTest {
        val vm = viewModel()
        collectFavorites(vm)

        vm.toggleFavorite("abasto")
        assertEquals(setOf("abasto"), favorites.current)

        vm.toggleFavorite("abasto")
        assertEquals(emptySet<String>(), favorites.current)
    }

    @Test
    fun unCambioDeLosContadoresActualizaElResultado() = runTest {
        val vm = viewModel()
        collecting(vm)

        parkingLots.emit(listOf(medrano.copy(freeSpots = 3), abasto, lejos))

        val first = (vm.result.value as DataResult.Success).value.first()
        assertEquals(3, first.parkingLot.freeSpots)
    }

    @Test
    fun cambiarDeVistaGuardaElModo() = runTest {
        val vm = viewModel()
        assertEquals(ViewMode.MAP, vm.viewMode.value)

        vm.setViewMode(ViewMode.LIST)

        assertEquals(ViewMode.LIST, vm.viewMode.value)
    }

    private fun TestScope.collectFavorites(vm: NearbyParkingLotsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.favoriteIds.collect {} }
    }
}
