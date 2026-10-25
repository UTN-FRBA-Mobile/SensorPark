package com.sensorpark.feature.parking

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
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
                    vm = ParkingLotDetailViewModel(FakeParkingLotRepository()),
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
    fun llenoNoDejaReservar() {
        val full = Samples.UTN_MEDRANO.copy(freeSpots = 0, freeSpotsByType = emptyMap())
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ParkingLotDetailScreen(
                    parkingLotId = full.id,
                    onReserve = {},
                    vm = ParkingLotDetailViewModel(FakeParkingLotRepository(listOf(full))),
                    spotsVm = SpotAvailabilityViewModel(FakeSpotAvailabilityRepository()),
                )
            }
        }

        compose.onNodeWithText("0 de 14 lugares libres").assertExists()
        compose.onNodeWithText("Sin lugares libres").performScrollTo().assertIsNotEnabled()
    }
}
