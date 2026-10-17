package com.sensorpark.feature.parking

import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SpotAvailabilityViewModelTest {
    private val repository = FakeSpotAvailabilityRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Como la pantalla: recolecta el estado desde el principio, así el listener ya está abierto.
    private fun TestScope.collecting(vm: SpotAvailabilityViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

    private fun spot(code: String, status: SpotStatus) = Spot("id-$code", code, SpotType.STANDARD, status)

    private fun availability(isFromCache: Boolean = false, vararg spots: Spot) =
        DataResult.Success(SpotAvailability(spots.toList(), isFromCache, Instant.parse("2026-10-17T14:05:00Z")))

    @Test
    fun empiezaCargando() = runTest {
        val vm = SpotAvailabilityViewModel(repository)

        assertEquals(SpotAvailabilityState.Loading, vm.state.value)
    }

    @Test
    fun muestraLosLugaresEnVivoYSeActualizanConCadaCambio() = runTest {
        val vm = SpotAvailabilityViewModel(repository)
        collecting(vm)
        vm.load("utn-medrano")

        repository.emit(availability(spots = arrayOf(spot("01", SpotStatus.FREE))))
        assertEquals(
            SpotAvailabilityState.Live(listOf(spot("01", SpotStatus.FREE)), Instant.parse("2026-10-17T14:05:00Z")),
            vm.state.value,
        )

        repository.emit(availability(spots = arrayOf(spot("01", SpotStatus.OCCUPIED))))
        assertEquals(
            listOf(SpotStatus.OCCUPIED),
            (vm.state.value as SpotAvailabilityState.Live).spots.map { it.status },
        )
        assertEquals(listOf("utn-medrano"), repository.requested)
    }

    @Test
    fun conDatosDeLaCacheMuestraSinConexionYAlVolverLaRedPasaAEnVivo() = runTest {
        val vm = SpotAvailabilityViewModel(repository)
        collecting(vm)
        vm.load("utn-medrano")

        repository.emit(availability(isFromCache = true, spots = arrayOf(spot("01", SpotStatus.FREE))))
        assertTrue(vm.state.value is SpotAvailabilityState.Offline)

        // Los mismos lugares, ya confirmados por el servidor.
        repository.emit(availability(isFromCache = false, spots = arrayOf(spot("01", SpotStatus.FREE))))
        assertTrue(vm.state.value is SpotAvailabilityState.Live)
    }

    @Test
    fun unErrorDelListenerSeMuestraSinCerrarLaApp() = runTest {
        val vm = SpotAvailabilityViewModel(repository)
        collecting(vm)
        vm.load("utn-medrano")

        repository.emit(DataResult.Failure(DataErrorReason.PERMISSION_DENIED))

        assertEquals(SpotAvailabilityState.Failure(DataErrorReason.PERMISSION_DENIED), vm.state.value)
    }

    @Test
    fun reintentarVuelveASuscribirse() = runTest {
        val vm = SpotAvailabilityViewModel(repository)
        collecting(vm)
        vm.load("utn-medrano")
        repository.emit(DataResult.Failure(DataErrorReason.NO_CONNECTION))

        vm.retry()

        assertEquals(2, repository.requested.size)
        assertEquals(SpotAvailabilityState.Loading, vm.state.value)
    }

    @Test
    fun noSeSuscribeHastaQueLaPantallaPideUnEstacionamiento() = runTest {
        val vm = SpotAvailabilityViewModel(repository)
        collecting(vm)

        assertEquals(emptyList<String>(), repository.requested)
    }

    @Test
    fun cierraElListenerCuandoLaPantallaDejaDeRecolectar() = runTest {
        val vm = SpotAvailabilityViewModel(repository)
        val job = collecting(vm)
        vm.load("utn-medrano")
        assertEquals(1, repository.activeCollectors)

        job.cancel()
        advanceTimeBy(6_000)

        assertEquals(0, repository.activeCollectors)
    }
}

/** Repositorio falso: la prueba decide qué emite y cuántos listeners quedan abiertos. */
private class FakeSpotAvailabilityRepository : SpotAvailabilityRepository {
    private val results = MutableSharedFlow<DataResult<SpotAvailability>>(extraBufferCapacity = 16)
    val requested = mutableListOf<String>()
    var activeCollectors = 0
        private set

    suspend fun emit(result: DataResult<SpotAvailability>) = results.emit(result)

    override fun observeSpots(parkingLotId: String): Flow<DataResult<SpotAvailability>> {
        requested += parkingLotId
        return flow {
            activeCollectors++
            try {
                results.collect { emit(it) }
            } finally {
                activeCollectors--
            }
        }
    }
}
