package com.sensorpark.feature.parking

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sensorpark.core.testing.FakeFavoritesRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * El detalle de un estacionamiento dibujado de verdad, con los repositorios falsos de T04: los
 * contadores, el plano de lugares que cambia en vivo, tocar un lugar libre y el aviso sin conexión.
 */
class ParkingLotDetailScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ComposeContentTestRule.show(
        spots: FakeSpotAvailabilityRepository = FakeSpotAvailabilityRepository(),
        lot: com.sensorpark.domain.model.ParkingLot = Samples.UTN_MEDRANO,
        onReserve: (String) -> Unit = {},
        onReserveSpot: (String, Spot) -> Unit = { _, _ -> },
    ) = setContent {
        SensorParkTheme(dynamicColor = false) {
            ParkingLotDetailScreen(
                parkingLotId = lot.id,
                onReserve = onReserve,
                onReserveSpot = onReserveSpot,
                vm = ParkingLotDetailViewModel(FakeParkingLotRepository(listOf(lot)), FakeFavoritesRepository()),
                spotsVm = SpotAvailabilityViewModel(spots),
            )
        }
    }

    private fun plan(vararg spots: Spot, isFromCache: Boolean = false): SpotAvailability =
        Samples.availability(*spots, isFromCache = isFromCache)

    @Test
    fun muestraLaOcupacionYLlevaAReservar() {
        var reserved: String? = null
        compose.show(onReserve = { reserved = it })

        compose.onNodeWithText(Samples.UTN_MEDRANO.name).assertExists()
        compose.onNodeWithText("9 de 14 lugares libres").assertExists()

        compose.onNodeWithText("Reservar un lugar").assertIsEnabled().performClick()
        assertEquals(Samples.UTN_MEDRANO.id, reserved)
    }

    @Test
    fun llenoNoDejaReservar() {
        val full = Samples.UTN_MEDRANO.copy(freeSpots = 0, freeSpotsByType = emptyMap())
        compose.show(lot = full)

        compose.onNodeWithText("0 de 14 lugares libres").assertExists()
        compose.onNodeWithText("Sin lugares libres").assertIsNotEnabled()
    }

    @Test
    fun elPlanoMuestraCadaLugarConSuEstadoYCambiaEnVivo() {
        val spots = FakeSpotAvailabilityRepository(
            plan(
                Samples.spot("01"),
                Samples.spot("02", status = SpotStatus.OCCUPIED),
                Samples.spot("11", type = SpotType.MOTORCYCLE),
            ),
        )
        compose.show(spots)

        compose.onNode(hasContentDescription("Lugar 11", substring = true)).performScrollTo()
        compose.onNodeWithContentDescription("Lugar 01, libre").assertExists()
        compose.onNodeWithContentDescription("Lugar 02, ocupado").assertExists()
        compose.onNodeWithContentDescription("Lugar 11, libre, Moto").assertExists()

        // Como el simulador: el 01 se ocupa y la celda cambia sola, sin recargar la pantalla.
        spots.emit(plan(Samples.spot("01", status = SpotStatus.OCCUPIED), Samples.spot("02"), Samples.spot("11")))
        compose.onNodeWithContentDescription("Lugar 01, ocupado").assertExists()
        compose.onNodeWithContentDescription("Lugar 02, libre").assertExists()
    }

    @Test
    fun unLugarLibreSeTocaParaReservarloYUnoOcupadoNo() {
        var picked: Spot? = null
        val spots = FakeSpotAvailabilityRepository(plan(Samples.spot("01"), Samples.spot("02", status = SpotStatus.OCCUPIED)))
        compose.show(spots, onReserveSpot = { _, spot -> picked = spot })

        compose.onNodeWithContentDescription("Lugar 02, ocupado").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Lugar 01, libre").assertIsEnabled().performClick()

        assertEquals("spot-01", picked?.id)
        assertEquals("01", picked?.code)
    }

    @Test
    fun sinConexionAvisaQueLosDatosPuedenEstarDesactualizados() {
        val spots = FakeSpotAvailabilityRepository(plan(Samples.spot("01"), isFromCache = true))
        compose.show(spots)

        compose.onNodeWithText("Sin conexión. Los datos pueden estar desactualizados.").assertExists()

        // Vuelve la red: Firestore reconecta solo y entrega los datos del servidor.
        spots.emit(plan(Samples.spot("01"), isFromCache = false))
        compose.onNodeWithText("En vivo").assertExists()
    }
}
