package com.sensorpark.feature.checkin

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.sensorpark.core.testing.FakeAlertMessages
import com.sensorpark.core.testing.FakeGeofenceRepository
import com.sensorpark.core.testing.FakeLocationRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReminderScheduler
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.notification.InMemoryNotificationRepository
import com.sensorpark.domain.usecase.CheckInUseCase
import com.sensorpark.domain.usecase.CheckOutUseCase
import com.sensorpark.domain.usecase.ReassignSpotUseCase
import com.sensorpark.domain.usecase.SyncReservationAlertsUseCase
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * Dibuja la pantalla de check-in de verdad en el emulador y hace el recorrido de la demo: llegar,
 * confirmar, que el sensor confirme el lugar y salir. Con los repositorios falsos de T04, sin
 * Firebase.
 */
class CheckInScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    // Diez minutos después de la hora de ejemplo: el conductor ya llegó y puede hacer check-in.
    private val arrival = Samples.NOW.plus(Duration.ofMinutes(10))
    private val clock = Clock.fixed(arrival, ZoneOffset.UTC)

    private val reservations = FakeReservationRepository(listOf(Samples.reservation()), clock = { arrival })
    private val parkingLots = FakeParkingLotRepository()
    private val spots = FakeSpotAvailabilityRepository(Samples.availability(Samples.spot("07")))
    private val location = FakeLocationRepository()

    @Test
    fun elRecorridoCompletoDeLaDemo() {
        val vm = viewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) { CheckInScreen(reservationId = "res-1", vm = vm) }
        }
        compose.mainClock.advanceTimeByFrame()

        // 1. La pantalla muestra el estacionamiento, el lugar, la franja y la tolerancia.
        compose.onNodeWithText("Check-in").assertExists()
        compose.onNodeWithText("UTN Medrano").assertExists()
        compose.onNodeWithText("Lugar 07").assertExists()
        compose.onNodeWithText("para hacer el check-in", substring = true).assertExists()
        compose.screenshot("05-check-in")

        // 2. Confirmar la llegada: la reserva pasa a en uso.
        compose.onNodeWithText("Confirmar que llegué").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Estás estacionado").assertExists()
        compose.onNodeWithText("Esperando que el sensor", substring = true).assertExists()
        compose.screenshot("06-estacionado")

        // 3. El sensor marca el lugar ocupado: "Lugar confirmado".
        spots.emit(Samples.availability(Samples.spot("07", SpotStatus.OCCUPIED)))
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Lugar confirmado por el sensor.").assertExists()
        compose.screenshot("07-lugar-confirmado")

        // 4. Salir: el resumen con la estadía y el importe.
        compose.onNodeWithText("Salir").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Listo, liberaste el lugar").assertExists()
        compose.onNodeWithText("Importe informativo", substring = true).assertExists()
        compose.screenshot("08-resumen")
    }

    @Test
    fun lejosDelEstacionamientoExplicaPorQueNoPuedeHacerCheckIn() {
        location.moveFarAway()
        val vm = viewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) { CheckInScreen(reservationId = "res-1", vm = vm) }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Confirmar que llegué").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Estás lejos del estacionamiento", substring = true).assertExists()
        compose.screenshot("09-lejos")
    }

    @Test
    fun siElLugarLoTomoOtroAutoOfrecePedirOtro() {
        spots.emit(Samples.availability(Samples.spot("07", SpotStatus.OCCUPIED)))
        val vm = viewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) { CheckInScreen(reservationId = "res-1", vm = vm) }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Tu lugar figura ocupado").assertExists()
        compose.onNodeWithText("Pedir otro lugar").assertExists()
        compose.screenshot("10-lugar-ocupado")

        compose.onNodeWithText("Pedir otro lugar").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("te asignamos otro lugar", substring = true).assertExists()
        compose.onNodeWithText("Lugar 09").assertExists()
    }

    private fun viewModel() = CheckInViewModel(
        reservations = reservations,
        parkingLots = parkingLots,
        spots = spots,
        location = location,
        checkInUseCase = CheckInUseCase(reservations, clock),
        checkOutUseCase = CheckOutUseCase(reservations),
        reassignSpotUseCase = ReassignSpotUseCase(reservations, clock),
        syncAlerts = SyncReservationAlertsUseCase(
            reservations = reservations,
            parkingLots = parkingLots,
            geofences = FakeGeofenceRepository(),
            reminders = FakeReminderScheduler(),
            notifications = InMemoryNotificationRepository(),
            messages = FakeAlertMessages,
            clock = clock,
        ),
        clock = clock,
    )
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
