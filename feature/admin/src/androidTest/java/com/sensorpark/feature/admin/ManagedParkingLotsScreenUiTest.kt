package com.sensorpark.feature.admin

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * "Mis estacionamientos" del administrador de estacionamiento, con los repositorios falsos de T04:
 * cada uno ve sólo los que lo tienen en `adminUids`, y el ADMIN de desarrollo los ve todos.
 */
class ManagedParkingLotsScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val mine = Samples.UTN_MEDRANO.copy(adminUids = listOf("encargado"))
    private val other = Samples.UTN_MEDRANO.copy(id = "abasto", name = "Playa Abasto", adminUids = listOf("otro"))
    private val repository = FakeParkingLotRepository(listOf(mine, other))

    @Test
    fun elEncargadoVeSoloLosSuyosYLosAbre() {
        var opened: String? = null
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ManagedParkingLotsScreen(
                    uid = "encargado",
                    seesAll = false,
                    onOpenParkingLot = { opened = it },
                    onCreateParkingLot = {},
                    vm = ManagedParkingLotsViewModel(repository),
                )
            }
        }

        compose.onNodeWithText("Mis estacionamientos").assertExists()
        compose.onNodeWithText("Playa Abasto").assertDoesNotExist()
        compose.onNodeWithText(mine.name).performClick()
        assertEquals(mine.id, opened)
    }

    @Test
    fun elAdminLosVeTodos() {
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ManagedParkingLotsScreen(
                    uid = "admin",
                    seesAll = true,
                    onOpenParkingLot = {},
                    onCreateParkingLot = {},
                    vm = ManagedParkingLotsViewModel(repository),
                )
            }
        }

        compose.onNodeWithText(mine.name).assertExists()
        compose.onNodeWithText("Playa Abasto").assertExists()
    }

    @Test
    fun sinEstacionamientosOfreceCrearElPrimero() {
        var create = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ManagedParkingLotsScreen(
                    uid = "nuevo",
                    seesAll = false,
                    onOpenParkingLot = {},
                    onCreateParkingLot = { create = true },
                    vm = ManagedParkingLotsViewModel(repository),
                )
            }
        }

        compose.onNodeWithText("Todavía no administrás ningún estacionamiento", substring = true).assertExists()
        // El botón flotante no expone "Crear" en su semántica (el texto queda en un nodo aparte que no
        // se puede tocar), así que se lo busca como lo único tocable de la pantalla vacía.
        compose.onNode(hasClickAction()).performClick()
        assertTrue(create)
    }
}
