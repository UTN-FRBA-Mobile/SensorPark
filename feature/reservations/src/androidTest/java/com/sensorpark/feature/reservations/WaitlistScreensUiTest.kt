package com.sensorpark.feature.reservations

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.FakeUserRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.usecase.AcceptWaitlistOfferUseCase
import com.sensorpark.domain.usecase.CancelReservationUseCase
import com.sensorpark.domain.usecase.CreateReservationUseCase
import com.sensorpark.domain.usecase.DeclineWaitlistOfferUseCase
import com.sensorpark.domain.usecase.JoinWaitlistUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * La lista de espera de punta a punta en pantalla (T18): sin lugar se anota, cuando se libera uno
 * ve la oferta con la cuenta regresiva y la acepta. Con los repositorios falsos de T04.
 */
class WaitlistScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Samples.NOW, ZoneOffset.UTC)
    private val reservations = FakeReservationRepository(clock = { Samples.NOW })
    private val parkingLots = FakeParkingLotRepository()

    @Test
    fun sinLugarSeAnotaEnLaListaDeEspera() {
        reservations.createResult = ReservationOutcome.Rejected(ReservationRejection.NO_SPOTS)
        val vm = ReserveViewModel(
            parkingLots = parkingLots,
            users = FakeUserRepository(),
            createReservation = CreateReservationUseCase(reservations, clock),
            enterWaitlist = JoinWaitlistUseCase(reservations, clock),
            spots = FakeSpotAvailabilityRepository(),
            clock = clock,
        )
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                ReserveScreen(parkingLotId = Samples.UTN_MEDRANO.id, onOpenMyReservations = {}, vm = vm)
            }
        }

        compose.onNodeWithText("Confirmar reserva").performScrollTo().performClick()
        compose.onNodeWithText("Avisarme si se libera un lugar").performScrollTo().performClick()

        compose.onNodeWithText("Estás en la lista de espera").performScrollTo().assertExists()
        compose.onNodeWithText("Sos el número 1 de la fila.").assertExists()
        compose.screenshot("15-lista-de-espera")
        assertEquals(1, reservations.joined.size)
    }

    @Test
    fun conLaOfertaEnMisReservasSeVeElTiempoParaAceptar() {
        reservations.emitWaitlist(listOf(Samples.offeredEntry(spotCode = "12")))
        val vm = MyReservationsViewModel(
            reservations = reservations,
            parkingLots = parkingLots,
            cancelReservation = CancelReservationUseCase(reservations),
            clock = clock,
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                MyReservationsScreen(reservationId = null, onCheckIn = {}, onOpenOffer = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("Listas de espera").assertExists()
        compose.onNodeWithText("¡Se liberó el lugar 12! Te quedan 5:00 para aceptarlo.").assertExists()
        compose.onNodeWithText("Ver la oferta").assertExists()
        compose.screenshot("16-mis-reservas-con-oferta")
    }

    @Test
    fun laOfertaMuestraLaCuentaRegresivaYAceptarLaConvierteEnReserva() {
        reservations.emitWaitlist(listOf(Samples.offeredEntry(spotCode = "12")))
        val vm = offerViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                WaitlistOfferScreen(entryId = "w-1", onOpenMyReservations = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("¡Se liberó un lugar!").assertExists()
        compose.onNodeWithText("5:00").assertExists()
        compose.onNodeWithText("Lugar 12", substring = true).assertExists()
        compose.screenshot("17-oferta")

        compose.onNodeWithText("Quiero el lugar").performScrollTo().performClick()
        compose.settle()

        compose.onNodeWithText("¡Listo, reservaste!").assertExists()
        compose.onNodeWithText("Lugar 12").assertExists()
        compose.screenshot("18-oferta-aceptada")
        assertEquals(listOf("w-1"), reservations.accepted)
    }

    @Test
    fun conLaOfertaVencidaNoSeOfreceAceptar() {
        // Como si el backend todavía no la hubiera dado por perdida: sigue OFFERED, pero ya venció.
        reservations.emitWaitlist(
            listOf(
                Samples.waitlistEntry(
                    status = WaitlistStatus.OFFERED,
                    offerExpiresAt = Samples.NOW.minus(Duration.ofSeconds(1)),
                ),
            ),
        )
        val vm = offerViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                WaitlistOfferScreen(entryId = "w-1", onOpenMyReservations = {}, vm = vm)
            }
        }
        compose.settle()

        compose.onNodeWithText("Se pasaron los 5 minutos", substring = true).assertExists()
        compose.onNodeWithText("Quiero el lugar").assertDoesNotExist()
    }

    private fun offerViewModel() = WaitlistOfferViewModel(
        reservations = reservations,
        parkingLots = parkingLots,
        acceptOffer = AcceptWaitlistOfferUseCase(reservations, clock),
        declineOffer = DeclineWaitlistOfferUseCase(reservations),
        clock = clock,
    )
}
