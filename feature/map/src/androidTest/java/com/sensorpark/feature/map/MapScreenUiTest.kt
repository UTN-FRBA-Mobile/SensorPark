package com.sensorpark.feature.map

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.sensorpark.core.testing.FakeFavoritesRepository
import com.sensorpark.core.testing.FakeLocationRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.SearchNearbyParkingLotsUseCase
import org.junit.Rule
import org.junit.Test

/**
 * La pestaña Buscar en el emulador, con los repositorios falsos de T04. El mapa necesita la key de
 * Maps para dibujar las calles; sin ella se ve gris, pero los marcadores y el resto de la pantalla
 * andan igual.
 */
class MapScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val abasto = Samples.UTN_MEDRANO.copy(
        id = "abasto",
        name = "Playa Abasto",
        address = "Av. Corrientes 3247",
        location = Coordinates(-34.6037, -58.4110),
        freeSpots = 0,
        freeSpotsByType = mapOf(SpotType.STANDARD to 0),
    )
    private val favorites = FakeFavoritesRepository()
    private val parkingLots = FakeParkingLotRepository(listOf(Samples.UTN_MEDRANO, abasto))

    private fun viewModel(location: FakeLocationRepository) =
        NearbyParkingLotsViewModel(SearchNearbyParkingLotsUseCase(parkingLots), location, favorites)

    @Test
    fun pideElPermisoExplicandoParaQueEsYSinPermisoSigueAndando() {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))
        compose.setContent {
            SensorParkTheme(dynamicColor = false) { MapScreen(onOpenParkingLot = {}, vm = vm) }
        }

        // Al llegar a Buscar se explica para qué es la ubicación, antes de pedirla.
        compose.onNodeWithText("Para mostrarte lo que tenés cerca").assertExists()
        compose.screenshot("11-permiso-ubicacion")

        // Si dice que no, la pantalla no queda en blanco: busca desde la zona por defecto y avisa.
        compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Sin tu ubicación", substring = true).assertExists()
        compose.onNodeWithText("Usar mi ubicación").assertExists()
    }

    @Test
    fun laListaMuestraLosCercanosConSuDisponibilidad() {
        val vm = viewModel(FakeLocationRepository(permission = false, reading = null))
        vm.onPermissionResult(granted = false, canAskAgain = true)
        vm.toggleMode()
        compose.setContent {
            SensorParkTheme(dynamicColor = false) { MapScreen(onOpenParkingLot = {}, vm = vm) }
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithText("UTN Medrano").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Estacionamientos cercanos").assertExists()
        compose.onNodeWithText("UTN Medrano").assertExists()
        compose.onNodeWithText("Playa Abasto").assertExists()
        compose.onNodeWithText("Completo").assertExists()
        compose.screenshot("12-buscar-lista")

        // Con el filtro de lugares libres, el completo desaparece.
        compose.onNodeWithText("Con lugares libres").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Playa Abasto").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("UTN Medrano").assertExists()
    }

    @Test
    fun conPermisoBuscaDesdeElTelefonoYMuestraElMapa() {
        val vm = viewModel(FakeLocationRepository())
        compose.setContent {
            SensorParkTheme(dynamicColor = false) { MapScreen(onOpenParkingLot = {}, vm = vm) }
        }

        compose.onNodeWithText("de vos", substring = true).assertExists()
        compose.onNodeWithText("Mapa").assertExists()
        compose.onNodeWithText("Lista").assertExists()
        compose.screenshot("13-buscar-mapa")
    }
}

/** Dónde quedan las capturas de la demo. Se bajan con `adb pull`. */
internal const val DEMO_SCREENSHOTS = "/sdcard/Download/sensorpark-demo"

/**
 * Guarda una captura de la pantalla, para la demo, con `screencap` por el shell de la
 * instrumentación: así también salen los diálogos y los archivos sobreviven a que Gradle
 * desinstale la app de test.
 */
internal fun ComposeContentTestRule.screenshot(name: String) {
    waitForIdle()
    Thread.sleep(800)
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    shell("mkdir -p $DEMO_SCREENSHOTS")
    shell("screencap -p $DEMO_SCREENSHOTS/$name.png")
}
