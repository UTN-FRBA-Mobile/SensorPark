package com.sensorpark.feature.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Stay
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.StayHistoryRepository
import com.sensorpark.domain.usecase.CancelReservationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Una reserva con el nombre de su estacionamiento, que vive en otra colección. */
data class ReservationCard(val reservation: Reservation, val parkingLotName: String) {
    /** Lo que queda de tolerancia para hacer el check-in, o cero si ya se pasó. */
    fun toleranceLeft(now: Instant): Duration =
        Duration.between(now, reservation.toleranceUntil).takeIf { !it.isNegative } ?: Duration.ZERO

    /** Lo que queda de la franja reservada, con la reserva en uso. */
    fun timeLeft(now: Instant): Duration =
        Duration.between(now, reservation.endsAt).takeIf { !it.isNegative } ?: Duration.ZERO
}

/** Una entrada abierta de la lista de espera, con el nombre de su estacionamiento. */
data class WaitlistCard(val entry: WaitlistEntry, val parkingLotName: String) {
    /** Lo que queda para contestar la oferta, o cero si no hay o ya se pasó. */
    fun offerTimeLeft(now: Instant): Duration =
        entry.offer?.let { Duration.between(now, it.expiresAt) }?.takeIf { !it.isNegative } ?: Duration.ZERO
}

data class MyReservationsState(
    val loading: Boolean = true,
    /** La que está pasando: confirmada esperando check-in, o en uso. */
    val active: ReservationCard? = null,
    val upcoming: List<ReservationCard> = emptyList(),
    val history: List<ReservationCard> = emptyList(),
    /** Sin poder leer Firestore (y sin su caché), las estadías guardadas en el teléfono (T19). */
    val savedStays: List<Stay> = emptyList(),
    /** Listas de espera en las que sigue anotado: esperando o con una oferta para contestar (T18). */
    val waitlist: List<WaitlistCard> = emptyList(),
    val failure: DataErrorReason? = null,
    val working: Boolean = false,
    val rejection: ReservationRejection? = null,
) {
    val isEmpty: Boolean
        get() = !loading && failure == null && active == null &&
            upcoming.isEmpty() && history.isEmpty() && waitlist.isEmpty()
}

/**
 * Pestaña "Mis reservas" (CU-09): la reserva activa arriba con sus acciones, después las próximas
 * y el historial. Se lee en vivo de Firestore, así que se actualiza sola cuando el backend cambia
 * el estado de una reserva, y sin red muestra lo último que se leyó.
 */
@HiltViewModel
class MyReservationsViewModel @Inject constructor(
    private val reservations: ReservationRepository,
    private val parkingLots: ParkingLotRepository,
    private val cancelReservation: CancelReservationUseCase,
    private val stayHistory: StayHistoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val action = MutableStateFlow(ActionState())

    val state: StateFlow<MyReservationsState> = combine(
        reservations.myReservations(),
        // Si la lista de espera no se puede leer, las reservas se muestran igual.
        reservations.myWaitlist().map { (it as? DataResult.Success)?.value.orEmpty() }.catch { emit(emptyList()) },
        parkingLots.parkingLots(),
        // El historial guardado en el teléfono (T19): se muestra si Firestore no puede dar nada.
        stayHistory.stays().catch { emit(emptyList()) },
        action,
    ) { mine, waitlist, lots, stays, action ->
        when (mine) {
            is DataResult.Failure -> MyReservationsState(loading = false, failure = mine.reason, savedStays = stays)
            is DataResult.Success -> {
                val names = (lots as? DataResult.Success)?.value.orEmpty().associate { it.id to it.name }
                val cards = mine.value.map { ReservationCard(it, names[it.parkingLotId].orEmpty()) }
                val now = clock.instant()
                val open = cards.filter { it.reservation.status.isActive }.sortedBy { it.reservation.arrivesAt }
                // La activa es la que está en uso o la que está por empezar; si falta mucho, no hay.
                val active = open.firstOrNull {
                    it.reservation.status == ReservationStatus.IN_USE ||
                        it.reservation.arrivesAt <= now.plus(ACTIVE_WINDOW)
                }
                MyReservationsState(
                    loading = false,
                    active = active,
                    upcoming = open.filter { it !== active },
                    history = cards
                        .filterNot { it.reservation.status.isActive }
                        .sortedByDescending { it.reservation.arrivesAt },
                    waitlist = waitlist
                        .filter { it.status.isOpen }
                        .sortedBy { it.arrivesAt }
                        .map { WaitlistCard(it, names[it.parkingLotId].orEmpty()) },
                    working = action.working,
                    rejection = action.rejection,
                    failure = action.failure,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyReservationsState())

    /** La hora de verdad, para las cuentas regresivas de la pantalla. */
    fun now(): Instant = clock.instant()

    fun dismissError() = action.update { it.copy(rejection = null, failure = null) }

    fun cancel(reservation: Reservation) {
        if (action.value.working) return
        viewModelScope.launch {
            action.update { it.copy(working = true, rejection = null, failure = null) }
            when (val outcome = cancelReservation(reservation)) {
                is ReservationOutcome.Success -> action.update { ActionState() }
                is ReservationOutcome.Rejected ->
                    action.update { it.copy(working = false, rejection = outcome.reason) }
                is ReservationOutcome.Failed ->
                    action.update { it.copy(working = false, failure = outcome.reason) }
            }
        }
    }

    /** Salir de la fila. Con una oferta pendiente, el lugar pasa al siguiente. */
    fun leaveWaitlist(entry: WaitlistEntry) {
        if (action.value.working) return
        viewModelScope.launch {
            action.update { it.copy(working = true, rejection = null, failure = null) }
            when (val outcome = reservations.leaveWaitlist(entry.id)) {
                is ReservationOutcome.Success -> action.update { ActionState() }
                is ReservationOutcome.Rejected ->
                    action.update { it.copy(working = false, rejection = outcome.reason) }
                is ReservationOutcome.Failed ->
                    action.update { it.copy(working = false, failure = outcome.reason) }
            }
        }
    }

    private data class ActionState(
        val working: Boolean = false,
        val rejection: ReservationRejection? = null,
        val failure: DataErrorReason? = null,
    )

    private companion object {
        /** Una reserva deja de ser "la de ahora" si todavía falta más que esto para llegar. */
        val ACTIVE_WINDOW: Duration = Duration.ofHours(2)
    }
}
