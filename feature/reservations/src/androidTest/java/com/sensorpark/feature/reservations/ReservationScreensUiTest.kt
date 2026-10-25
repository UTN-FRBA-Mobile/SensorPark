package com.sensorpark.feature.reservations

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.FakeUserRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.usecase.CancelReservationUseCase
import com.sensorpark.domain.usecase.CreateReservationUseCase
import com.sensorpark.domain.usecase.JoinWaitlistUseCase
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * Dibuja las pantallas de reservas de verdad en el emulador y las maneja como lo haría una
 * persona. Usa los repositorios falsos de T04: no hace falta Firebase ni `google-services.json`,
 * porque `feature/reservations` es una librería y el plugin de Google Services sólo está en `app`.
 *
 * Además guarda una captura de cada pantalla en los archivos externos de la app, para la demo.
 */
class ReservationScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Samples.NOW, ZoneOffset.UTC)
    private val reservations = FakeReservationRepository()
    private val parkingLots = FakeParkingLotRepository()
    private val users = FakeUserRepository()

    @Test
    fun reservarMuestraElFormularioYConfirmaLaReserva() {
        val vm = ReserveViewModel(
            parkingLots = parkingLots,
            users = users,
            createReservation = CreateReservationUseCase(reservations, clock),
            enterWaitlist = JoinWaitlistUseCase(reservations, clock),
            spots = FakeSpotAvailabilityRepository(),
            clock = clock,
        )
        // Esta pantalla no tiene cuenta regresiva, así que el reloj automático de Compose
        // sincroniza solo: no hace falta pedir frames a mano.
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ReserveScreen(parkingLotId = Samples.UTN_MEDRANO.id, onOpenMyReservations = {}, vm = vm)
            }
        }

        // El formulario muestra el estacionamiento, la franja y la tarifa estimada.
        compose.onNodeWithText("Reservar").assertExists()
        compose.onNodeWithText("UTN Medrano").assertExists()
        compose.onNodeWithText("¿Cuánto te quedás?").assertExists()
        compose.onNodeWithText("Tarifa estimada", substring = true).assertExists()
        compose.screenshot("01-reservar")

        // Elegir dos horas cambia la tarifa estimada.
        compose.onNodeWithText("2 h").performScrollTo().performClick()
        compose.onNodeWithText("Tarifa estimada: $2.400").assertExists()

        // Confirmar lleva al resumen con el lugar asignado y la tolerancia. El botón está al final
        // del formulario, así que hay que bajar hasta él: un click fuera de la pantalla no hace nada.
        compose.onNodeWithText("Confirmar reserva").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("¡Listo, reservaste!").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("¡Listo, reservaste!").assertExists()
        compose.onNodeWithText("Lugar 07").assertExists()
        compose.onNodeWithText("para hacer el check-in", substring = true).assertExists()
        compose.screenshot("02-reserva-confirmada")
    }

    @Test
    fun misReservasMuestraLaActivaConSuCheckIn() {
        reservations.emit(listOf(Samples.reservation()))
        val vm = myReservationsViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                MyReservationsScreen(reservationId = null, onCheckIn = {}, onOpenOffer = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("Mis reservas").assertIsDisplayed()
        compose.onNodeWithText("Ahora").assertIsDisplayed()
        compose.onNodeWithText("UTN Medrano").assertExists()
        compose.onNodeWithText("Confirmada").assertExists()
        compose.onNodeWithText("para hacer el check-in", substring = true).assertExists()
        compose.onNodeWithText("Hacer check-in").assertExists()
        compose.screenshot("03-mis-reservas")
    }

    @Test
    fun cancelarPideConfirmacionYDejaLaReservaEnElHistorial() {
        reservations.emit(listOf(Samples.reservation()))
        val vm = myReservationsViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                MyReservationsScreen(reservationId = null, onCheckIn = {}, onOpenOffer = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("Cancelar reserva").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("¿Cancelar la reserva?").assertExists()
        compose.screenshot("04-cancelar")

        compose.onNodeWithText("Sí, cancelar").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Historial").assertExists()
        compose.onNodeWithText("Cancelada").assertExists()
    }

    @Test
    fun unaReservaEnUsoOfreceElCheckOut() {
        reservations.emit(listOf(Samples.reservation(status = ReservationStatus.IN_USE)))
        val vm = myReservationsViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                MyReservationsScreen(reservationId = null, onCheckIn = {}, onOpenOffer = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("En uso").assertExists()
        compose.onNodeWithText("de la franja reservada", substring = true).assertExists()
        compose.onNodeWithText("Ver la estadía y salir").assertExists()
    }

    @Test
    fun sinReservasLoDiceEnVezDeQuedarseEnBlanco() {
        val vm = myReservationsViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                MyReservationsScreen(reservationId = null, onCheckIn = {}, onOpenOffer = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("Todavía no tenés reservas", substring = true).assertExists()
    }

    private fun myReservationsViewModel() = MyReservationsViewModel(
        reservations = reservations,
        parkingLots = parkingLots,
        cancelReservation = CancelReservationUseCase(reservations),
        // Diez minutos después de la hora de ejemplo: la reserva ya está en tolerancia.
        clock = Clock.fixed(Samples.NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
    )
}

/**
 * Deja que la composición se asiente: con el reloj de Compose en manual, la primera emisión del
 * StateFlow del ViewModel llega recién en el frame siguiente.
 */
internal fun ComposeContentTestRule.settle() {
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
}

/** Dónde quedan las capturas de la demo. Se bajan con `adb pull`. */
internal const val DEMO_SCREENSHOTS = "/sdcard/Download/sensorpark-demo"

/**
 * Guarda una captura de la pantalla, para la demo. Usa `screencap` por el shell de la
 * instrumentación en vez de capturar el árbol de Compose, por dos motivos: así también salen los
 * diálogos, que son otra ventana, y los archivos quedan fuera de la app de test, que Gradle
 * desinstala al terminar.
 */
internal fun ComposeContentTestRule.screenshot(name: String) {
    // Con el reloj de Compose en manual hay que pedir un frame y darle tiempo a llegar a la
    // pantalla; si no, `screencap` saca el frame anterior.
    mainClock.advanceTimeByFrame()
    waitForIdle()
    Thread.sleep(500)
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    // Leer la salida hasta el final es lo que espera a que el comando termine.
    fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }

    shell("mkdir -p $DEMO_SCREENSHOTS")
    shell("screencap -p $DEMO_SCREENSHOTS/$name.png")
}
