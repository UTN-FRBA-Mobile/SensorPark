package com.sensorpark.feature.parking

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.sensorpark.core.testing.FakeFavoritesRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * El detalle de un estacionamiento dibujado de verdad, con los repositorios falsos de T04: los
 * contadores, el botón de reservar y que se apague cuando no queda lugar.
 */
class ParkingLotDetailScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun muestraLaOcupacionYLlevaAReservar() {
        var reserved: String? = null
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ParkingLotDetailScreen(
                    parkingLotId = Samples.UTN_MEDRANO.id,
                    onReserve = { reserved = it },
                    vm = ParkingLotDetailViewModel(FakeParkingLotRepository(), FakeFavoritesRepository()),
                    spotsVm = SpotAvailabilityViewModel(FakeSpotAvailabilityRepository()),
                )
            }
        }

        compose.onNodeWithText(Samples.UTN_MEDRANO.name).assertExists()
        compose.onNodeWithText("9 de 14 lugares libres").assertExists()

        compose.onNodeWithText("Reservar un lugar").performScrollTo().assertIsEnabled().performClick()
        assertEquals(Samples.UTN_MEDRANO.id, reserved)
    }

    @Test
    fun elPlanoCambiaEnVivoYUnLugarLibreLlevaAReservarEseLugar() {
        val spots = FakeSpotAvailabilityRepository(
            Samples.availability(
                Samples.spot("01"),
                Samples.spot("02", status = SpotStatus.OCCUPIED),
                Samples.spot("11", type = SpotType.MOTORCYCLE),
            ),
        )
        var picked: String? = null
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ParkingLotDetailScreen(
                    parkingLotId = Samples.UTN_MEDRANO.id,
                    onReserve = {},
                    onReserveSpot = { _, spotId -> picked = spotId },
                    vm = ParkingLotDetailViewModel(FakeParkingLotRepository(), FakeFavoritesRepository()),
                    spotsVm = SpotAvailabilityViewModel(spots),
                )
            }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Lugar 11", substring = true))
        compose.onNodeWithContentDescription("Lugar 02, ocupado.").assertExists()
        compose.onNodeWithContentDescription("Lugar 11, libre, Moto", substring = true).assertExists()

        // Como el simulador: el 01 se ocupa y la celda cambia sola, sin recargar.
        spots.emit(Samples.availability(Samples.spot("01", status = SpotStatus.OCCUPIED), Samples.spot("02")))
        compose.onNodeWithContentDescription("Lugar 01, ocupado.").assertExists()

        compose.onNodeWithContentDescription("Lugar 02, libre", substring = true).performClick()
        assertEquals("spot-02", picked)
    }

    @Test
    fun llenoNoDejaReservar() {
        val full = Samples.UTN_MEDRANO.copy(freeSpots = 0, freeSpotsByType = emptyMap())
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ParkingLotDetailScreen(
                    parkingLotId = full.id,
                    onReserve = {},
                    vm = ParkingLotDetailViewModel(FakeParkingLotRepository(listOf(full)), FakeFavoritesRepository()),
                    spotsVm = SpotAvailabilityViewModel(FakeSpotAvailabilityRepository()),
                )
            }
        }

        compose.onNodeWithText("0 de 14 lugares libres").assertExists()
        compose.onNodeWithText("Sin lugares libres").performScrollTo().assertIsNotEnabled()
    }
}
