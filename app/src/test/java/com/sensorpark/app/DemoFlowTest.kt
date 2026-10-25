package com.sensorpark.app

import com.sensorpark.core.testing.FakeAlertMessages
import com.sensorpark.core.testing.FakeGeofenceRepository
import com.sensorpark.core.testing.FakeLocationRepository
import com.sensorpark.core.testing.FakeParkingLotRepository
import com.sensorpark.core.testing.FakeReminderScheduler
import com.sensorpark.core.testing.FakeReservationRepository
import com.sensorpark.core.testing.FakeStayHistoryRepository
import com.sensorpark.core.testing.FakeSpotAvailabilityRepository
import com.sensorpark.core.testing.FakeUserRepository
import com.sensorpark.core.testing.Samples
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.notification.InMemoryNotificationRepository
import com.sensorpark.domain.reservation.ReservationRules
import com.sensorpark.domain.usecase.CancelReservationUseCase
import com.sensorpark.domain.usecase.CheckInUseCase
import com.sensorpark.domain.usecase.CheckOutUseCase
import com.sensorpark.domain.usecase.CreateReservationUseCase
import com.sensorpark.domain.usecase.JoinWaitlistUseCase
import com.sensorpark.domain.usecase.ReassignSpotUseCase
import com.sensorpark.domain.usecase.SyncReservationAlertsUseCase
import com.sensorpark.feature.checkin.CheckInMessage
import com.sensorpark.feature.checkin.CheckInViewModel
import com.sensorpark.feature.reservations.MyReservationsViewModel
import com.sensorpark.feature.reservations.ReserveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * El guion de la demo, de punta a punta y en un solo test: reservar, que el teléfono avise y
 * registre la geocerca, llegar, hacer check-in, que el sensor confirme el lugar y hacer check-out.
 *
 * Cruza las tres pantallas (reservar, mis reservas y check-in) y las tres tareas de P5, así que
 * vive en `app`, que es el único módulo que las conoce a todas. Usa los repositorios falsos de
 * T04, sin Android ni Firebase: lo que prueba es que las piezas encajan.
 *
 * Si este test se pone en rojo, la demo se rompe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoFlowTest {

    private val now = Samples.NOW
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val reservations = FakeReservationRepository()
    private val parkingLots = FakeParkingLotRepository()
    private val spots = FakeSpotAvailabilityRepository(Samples.availability(Samples.spot("07")))
    private val users = FakeUserRepository()
    private val location = FakeLocationRepository()
    private val geofences = FakeGeofenceRepository()
    private val reminders = FakeReminderScheduler()
    private val notifications = InMemoryNotificationRepository()

    private val syncAlerts = SyncReservationAlertsUseCase(
        reservations = reservations,
        parkingLots = parkingLots,
        geofences = geofences,
        reminders = reminders,
        notifications = notifications,
        messages = FakeAlertMessages,
        clock = clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collecting(flow: kotlinx.coroutines.flow.Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect() }
    }

    private fun reserveViewModel() = ReserveViewModel(
        parkingLots = parkingLots,
        users = users,
        createReservation = CreateReservationUseCase(reservations, clock),
        enterWaitlist = JoinWaitlistUseCase(reservations, clock),
        spots = FakeSpotAvailabilityRepository(),
        clock = clock,
    )

    private fun myReservationsViewModel() = MyReservationsViewModel(
        reservations = reservations,
        parkingLots = parkingLots,
        cancelReservation = CancelReservationUseCase(reservations),
        stayHistory = FakeStayHistoryRepository(),
        clock = clock,
    )

    /**
     * El check-in pasa cuando el conductor llega, no cuando reserva: por eso recibe su propia
     * hora. Con la hora de la reserva el botón está deshabilitado, que es lo correcto.
     */
    private fun checkInViewModel(at: Instant = now): CheckInViewModel {
        val clockAtArrival = Clock.fixed(at, ZoneOffset.UTC)
        return CheckInViewModel(
            reservations = reservations,
            parkingLots = parkingLots,
            spots = spots,
            location = location,
            checkInUseCase = CheckInUseCase(reservations, clockAtArrival),
            checkOutUseCase = CheckOutUseCase(reservations),
            reassignSpotUseCase = ReassignSpotUseCase(reservations, clockAtArrival),
            syncAlerts = syncAlerts,
            clock = clockAtArrival,
        )
    }

    @Test
    fun laDemoCompleta() = runTest {
        // 1. El conductor reserva para dentro de 40 minutos, una hora de estadía.
        val arrival = now.plus(Duration.ofMinutes(40))
        val reserve = reserveViewModel()
        collecting(reserve.state)
        reserve.load(Samples.UTN_MEDRANO.id)
        reserve.setTime(LocalTime.ofInstant(arrival, ReservationRules.ZONE))
        reserve.setDuration(60)

        assertEquals(1_200, reserve.state.value.estimatedAmount)

        reserve.confirm()

        val confirmation = reserve.state.value.confirmation
        assertNotNull("Tendría que haber confirmado la reserva", confirmation)
        assertEquals("07", confirmation!!.spotCode)
        val reservationId = confirmation.reservationId

        // 2. El teléfono se entera: avisa, programa el recordatorio y registra la geocerca.
        syncAlerts()

        assertEquals(
            listOf(NotificationType.RESERVATION),
            notifications.shown.value.map { it.type },
        )
        assertEquals(
            "El recordatorio va 30 minutos antes de la llegada",
            confirmation.arrivesAt.minus(Duration.ofMinutes(30)),
            reminders.scheduled[reservationId],
        )
        val geofence = geofences.find(reservationId)
        assertNotNull("Tendría que haber geocerca para la reserva", geofence)
        assertEquals(Samples.UTN_MEDRANO.location, geofence!!.center)
        assertEquals(Samples.UTN_MEDRANO.geofenceRadiusMeters, geofence.radiusMeters)
        assertEquals("07", geofence.spotCode)

        // 3. "Mis reservas" la muestra como la de ahora, con el check-in habilitado.
        val myReservations = myReservationsViewModel()
        collecting(myReservations.state)

        val active = myReservations.state.value.active
        assertNotNull("La reserva tendría que estar arriba de todo", active)
        assertEquals(reservationId, active!!.reservation.id)
        assertEquals("UTN Medrano", active.parkingLotName)
        assertTrue("Tendría que quedar tolerancia", active.toleranceLeft(now) > Duration.ZERO)
        assertTrue(myReservations.state.value.history.isEmpty())

        // 4. Llega al estacionamiento: la geocerca dispara el aviso de llegada con su lugar.
        notifications.notify(FakeAlertMessages.arrival(geofence))
        val arrivalNotification = notifications.shown.value.last()
        assertEquals(NotificationType.ARRIVAL, arrivalNotification.type)
        assertTrue("El aviso tiene que decir qué lugar le tocó", arrivalNotification.body.contains("07"))

        // 5. Pasan los 40 minutos y llega: ahora sí se puede hacer check-in.
        val checkIn = checkInViewModel(at = arrival)
        collecting(checkIn.state)
        checkIn.load(reservationId)

        assertTrue("El botón de check-in tendría que estar habilitado", checkIn.state.value.canCheckIn(arrival))
        checkIn.checkIn()

        assertNull("No tendría que haber ningún error", checkIn.state.value.message)
        assertEquals(listOf(reservationId), reservations.checkedIn)
        assertEquals(ReservationStatus.IN_USE, checkIn.state.value.reservation?.status)

        // La geocerca y el recordatorio ya no hacen falta, y aparece el aviso persistente.
        assertTrue(geofences.removed.contains(reservationId))
        assertTrue(reminders.cancelled.contains(reservationId))
        val stay = notifications.shown.value.first { it.key == FakeAlertMessages.stayKey(reservationId) }
        assertTrue("El aviso de la estadía tiene que ser persistente", stay.ongoing)

        // 6. El sensor marca el lugar ocupado: la pantalla muestra "Lugar confirmado".
        assertFalse(checkIn.state.value.spotConfirmed)
        spots.emit(Samples.availability(Samples.spot("07", SpotStatus.OCCUPIED)))
        assertTrue("El sensor tendría que confirmar el lugar", checkIn.state.value.spotConfirmed)

        // 7. Check-out: resumen con la estadía y el importe, y se va el aviso persistente.
        checkIn.checkOut()

        assertEquals(listOf(reservationId), reservations.checkedOut)
        assertNotNull("Tendría que mostrar el resumen", checkIn.state.value.summary)
        assertEquals(ReservationStatus.FINISHED, reservations.current.single().status)
        assertNull(
            "El aviso persistente tiene que desaparecer",
            notifications.shown.value.firstOrNull { it.key == FakeAlertMessages.stayKey(reservationId) },
        )

        // 8. Y la reserva pasa al historial de "Mis reservas".
        assertNull(myReservations.state.value.active)
        assertEquals(listOf(reservationId), myReservations.state.value.history.map { it.reservation.id })
    }

    @Test
    fun laDemoDelCasoFeo_llegaLejosYElLugarLoTomaOtroAuto() = runTest {
        val reservation = Samples.reservation(arrivesAt = now)
        reservations.emit(listOf(reservation))
        val checkIn = checkInViewModel()
        collecting(checkIn.state)
        checkIn.load(reservation.id)

        // Todavía no llegó: el check-in se rechaza y explica por qué.
        location.moveFarAway()
        checkIn.checkIn()
        assertEquals(CheckInMessage.TOO_FAR, checkIn.state.value.message)
        assertTrue("No tiene que llamar al backend si sabe que está lejos", reservations.checkedIn.isEmpty())

        // Llega, pero el sensor dice que su lugar lo ocupó otro auto.
        spots.emit(Samples.availability(Samples.spot("07", SpotStatus.OCCUPIED)))
        assertTrue(checkIn.state.value.spotTaken)

        // Pide otro lugar y el backend se lo da.
        checkIn.requestAnotherSpot()
        assertEquals(CheckInMessage.SPOT_REASSIGNED, checkIn.state.value.message)
        assertEquals("09", checkIn.state.value.reservation?.spotCode)
    }

    @Test
    fun cancelarUnaReservaLimpiaElAvisoLaGeocercaYElRecordatorio() = runTest {
        val reserve = reserveViewModel()
        collecting(reserve.state)
        reserve.load(Samples.UTN_MEDRANO.id)
        reserve.confirm()
        syncAlerts()
        val reservationId = reserve.state.value.confirmation!!.reservationId
        assertNotNull(geofences.find(reservationId))

        val myReservations = myReservationsViewModel()
        collecting(myReservations.state)
        myReservations.cancel(reservations.current.single())
        syncAlerts()

        assertNull("La geocerca tiene que irse", geofences.find(reservationId))
        assertTrue(reminders.cancelled.contains(reservationId))
        assertEquals(
            ReservationStatus.CANCELLED,
            reservations.myReservations().first().let {
                (it as com.sensorpark.domain.model.DataResult.Success).value.single().status
            },
        )
    }

    @Test
    fun conPrioridadDeclaradaSeOfrecenLosLugaresPreferenciales() = runTest {
        users.setCategory(Category.REDUCED_MOBILITY)
        val reserve = reserveViewModel()
        collecting(reserve.state)
        reserve.load(Samples.UTN_MEDRANO.id)

        assertTrue(reserve.state.value.spotTypes.contains(com.sensorpark.domain.model.SpotType.PRIORITY))
    }
}
