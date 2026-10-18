package com.sensorpark.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.repository.LocationRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import com.sensorpark.domain.reservation.ReservationRules
import com.sensorpark.domain.usecase.CheckInUseCase
import com.sensorpark.domain.usecase.CheckOutUseCase
import com.sensorpark.domain.usecase.ReassignSpotUseCase
import com.sensorpark.domain.usecase.SyncReservationAlertsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Lo que la pantalla tiene que decirle al conductor. La pantalla los traduce a textos. */
enum class CheckInMessage {
    /** Está lejos del estacionamiento. */
    TOO_FAR,

    /** Está lejos y además el GPS trae poco margen de precisión. */
    TOO_FAR_LOW_ACCURACY,
    TOO_EARLY,
    EXPIRED,
    NO_LOCATION_PERMISSION,
    NO_LOCATION,
    NO_CONNECTION,
    UNKNOWN_ERROR,

    /** El lugar asignado figura ocupado por otro auto. */
    SPOT_TAKEN,

    /** Se pidió otro lugar y no quedaba ninguno del mismo tipo. */
    NO_OTHER_SPOT,

    /** Se reasignó: la pantalla ya muestra el lugar nuevo. */
    SPOT_REASSIGNED,
}

data class CheckInState(
    val loading: Boolean = true,
    val reservation: Reservation? = null,
    val parkingLot: ParkingLot? = null,
    /** El lugar asignado tal como lo ve el sensor; null mientras no se sepa. */
    val spot: Spot? = null,
    val notFound: Boolean = false,
    val failure: DataErrorReason? = null,
    /** Hay una llamada al backend en curso. */
    val working: Boolean = false,
    val message: CheckInMessage? = null,
    /** El resumen del check-out, una vez hecho. */
    val summary: CheckOutSummary? = null,
) {
    val isInUse: Boolean get() = reservation?.status == ReservationStatus.IN_USE

    /** Después del check-in, el sensor confirma que el auto está en el lugar. */
    val spotConfirmed: Boolean get() = isInUse && spot?.status == SpotStatus.OCCUPIED

    /** Antes del check-in, un lugar ocupado quiere decir que lo tomó otro auto. */
    val spotTaken: Boolean
        get() = reservation?.status == ReservationStatus.CONFIRMED && spot?.status == SpotStatus.OCCUPIED

    fun toleranceLeft(now: Instant): Duration {
        val until = reservation?.toleranceUntil ?: return Duration.ZERO
        val left = Duration.between(now, until)
        return if (left.isNegative) Duration.ZERO else left
    }

    /** El botón de confirmar sólo se habilita dentro de la ventana que acepta el backend. */
    fun canCheckIn(now: Instant): Boolean {
        val reservation = reservation ?: return false
        return !working &&
            reservation.status == ReservationStatus.CONFIRMED &&
            now >= reservation.arrivesAt.minus(ReservationRules.EARLY_CHECK_IN) &&
            now <= reservation.toleranceUntil
    }
}

/** Lo que cambia por una acción del usuario, aparte de lo que llega de la base. */
private data class ActionState(
    val working: Boolean = false,
    val message: CheckInMessage? = null,
    val summary: CheckOutSummary? = null,
)

/**
 * Check-in (CU-06) y check-out (CU-08) de una reserva. La pantalla se abre desde "Mis reservas" o
 * desde el aviso de llegada, y muestra una cosa u otra según el estado de la reserva.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val reservations: ReservationRepository,
    private val parkingLots: ParkingLotRepository,
    private val spots: SpotAvailabilityRepository,
    private val location: LocationRepository,
    private val checkInUseCase: CheckInUseCase,
    private val checkOutUseCase: CheckOutUseCase,
    private val reassignSpotUseCase: ReassignSpotUseCase,
    private val syncAlerts: SyncReservationAlertsUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val reservationId = MutableStateFlow<String?>(null)
    private val action = MutableStateFlow(ActionState())

    val state: StateFlow<CheckInState> = combine(
        reservationId.filterNotNull().flatMapLatest { observe(it) },
        action,
    ) { data, action ->
        data.copy(working = action.working, message = action.message, summary = action.summary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInState())

    fun load(reservationId: String) {
        this.reservationId.value = reservationId
    }

    /** La hora de verdad, para la cuenta regresiva de la tolerancia. */
    fun now(): Instant = clock.instant()

    fun dismissMessage() = action.update { it.copy(message = null) }

    fun checkIn() {
        val current = state.value
        val reservation = current.reservation ?: return
        val lot = current.parkingLot ?: return
        if (current.working) return

        viewModelScope.launch {
            action.update { it.copy(working = true, message = null) }
            val reading = location.currentLocation()
            if (reading == null) {
                val message = if (location.hasLocationPermission()) {
                    CheckInMessage.NO_LOCATION
                } else {
                    CheckInMessage.NO_LOCATION_PERMISSION
                }
                action.update { it.copy(working = false, message = message) }
                return@launch
            }

            val outcome = checkInUseCase(reservation, lot, reading.coordinates)
            val message = when (outcome) {
                is ReservationOutcome.Success -> null
                is ReservationOutcome.Rejected -> when (outcome.reason) {
                    ReservationRejection.TOO_FAR ->
                        if (reading.accuracyMeters > LOW_ACCURACY_METERS) CheckInMessage.TOO_FAR_LOW_ACCURACY
                        else CheckInMessage.TOO_FAR
                    ReservationRejection.TOO_EARLY -> CheckInMessage.TOO_EARLY
                    ReservationRejection.EXPIRED -> CheckInMessage.EXPIRED
                    else -> CheckInMessage.UNKNOWN_ERROR
                }
                is ReservationOutcome.Failed ->
                    if (outcome.reason == DataErrorReason.NO_CONNECTION) CheckInMessage.NO_CONNECTION
                    else CheckInMessage.UNKNOWN_ERROR
            }
            action.update { it.copy(working = false, message = message) }
            // Pasó a en uso: saca la geocerca y el recordatorio, y muestra el aviso persistente.
            if (outcome is ReservationOutcome.Success) runCatching { syncAlerts() }
        }
    }

    /** El lugar asignado lo tomó otro auto: pedirle al backend otro del mismo tipo. */
    fun requestAnotherSpot() {
        val current = state.value
        val reservation = current.reservation ?: return
        if (current.working) return

        viewModelScope.launch {
            action.update { it.copy(working = true, message = null) }
            val spot = current.spot
            val message = when (val outcome = reassignSpotUseCase(reservation, spot)) {
                is ReservationOutcome.Success -> CheckInMessage.SPOT_REASSIGNED
                is ReservationOutcome.Rejected -> when (outcome.reason) {
                    ReservationRejection.NO_SPOTS -> CheckInMessage.NO_OTHER_SPOT
                    ReservationRejection.EXPIRED -> CheckInMessage.EXPIRED
                    else -> CheckInMessage.UNKNOWN_ERROR
                }
                is ReservationOutcome.Failed ->
                    if (outcome.reason == DataErrorReason.NO_CONNECTION) CheckInMessage.NO_CONNECTION
                    else CheckInMessage.UNKNOWN_ERROR
            }
            action.update { it.copy(working = false, message = message) }
        }
    }

    fun checkOut() {
        val reservation = state.value.reservation ?: return
        if (state.value.working) return

        viewModelScope.launch {
            action.update { it.copy(working = true, message = null) }
            when (val outcome = checkOutUseCase(reservation)) {
                is ReservationOutcome.Success ->
                    action.update { it.copy(working = false, summary = outcome.value) }
                is ReservationOutcome.Rejected ->
                    action.update { it.copy(working = false, message = CheckInMessage.UNKNOWN_ERROR) }
                is ReservationOutcome.Failed -> {
                    val message = if (outcome.reason == DataErrorReason.NO_CONNECTION) {
                        CheckInMessage.NO_CONNECTION
                    } else {
                        CheckInMessage.UNKNOWN_ERROR
                    }
                    action.update { it.copy(working = false, message = message) }
                }
            }
            // La reserva terminó: saca el aviso persistente.
            runCatching { syncAlerts() }
        }
    }

    private fun observe(id: String) = reservations.myReservations().flatMapLatest { result ->
        when (result) {
            is DataResult.Failure -> flowOf(CheckInState(loading = false, failure = result.reason))
            is DataResult.Success -> {
                val reservation = result.value.firstOrNull { it.id == id }
                if (reservation == null) {
                    flowOf(CheckInState(loading = false, notFound = true))
                } else {
                    withLotAndSpot(reservation)
                }
            }
        }
    }

    private fun withLotAndSpot(reservation: Reservation) = combine(
        parkingLots.parkingLot(reservation.parkingLotId),
        spots.observeSpots(reservation.parkingLotId),
    ) { lotResult, spotsResult ->
        CheckInState(
            loading = false,
            reservation = reservation,
            parkingLot = (lotResult as? DataResult.Success)?.value,
            spot = (spotsResult as? DataResult.Success)?.value
                ?.spots?.firstOrNull { it.id == reservation.spotId },
            failure = (lotResult as? DataResult.Failure)?.reason,
        )
    }

    /** Por encima de este margen conviene avisar que el GPS no está fino, no sólo que está lejos. */
    private companion object {
        const val LOW_ACCURACY_METERS = 50f
    }
}
