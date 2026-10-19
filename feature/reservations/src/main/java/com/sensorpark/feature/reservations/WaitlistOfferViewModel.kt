package com.sensorpark.feature.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.usecase.AcceptWaitlistOfferUseCase
import com.sensorpark.domain.usecase.DeclineWaitlistOfferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class WaitlistOfferState(
    val loading: Boolean = true,
    /** Null si la entrada no es del usuario o ya no existe. */
    val entry: WaitlistEntry? = null,
    val parkingLotName: String = "",
    val working: Boolean = false,
    val rejection: ReservationRejection? = null,
    val failure: DataErrorReason? = null,
    /** Cuando está, aceptó y la oferta es ahora una reserva. */
    val confirmation: ReservationConfirmation? = null,
    val declined: Boolean = false,
) {
    /** Lo que queda para contestar, o cero si no hay oferta o ya se pasó. */
    fun timeLeft(now: Instant): Duration =
        entry?.offer?.let { Duration.between(now, it.expiresAt) }?.takeIf { !it.isNegative } ?: Duration.ZERO
}

/**
 * Pantalla de la oferta de la lista de espera (T18): se liberó un lugar para la franja que pidió y
 * se lo guardan unos minutos. Se llega desde el aviso (`sensorpark://waitlist-offer/{id}`) o desde
 * "Mis reservas". La entrada se lee en vivo: si el backend la da por vencida, la pantalla se entera.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WaitlistOfferViewModel @Inject constructor(
    private val reservations: ReservationRepository,
    parkingLots: ParkingLotRepository,
    private val acceptOffer: AcceptWaitlistOfferUseCase,
    private val declineOffer: DeclineWaitlistOfferUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val entryId = MutableStateFlow<String?>(null)
    private val action = MutableStateFlow(ActionState())

    val state: StateFlow<WaitlistOfferState> = combine(
        entryId.filterNotNull().flatMapLatest { reservations.myWaitlist() },
        parkingLots.parkingLots(),
        entryId,
        action,
    ) { mine, lots, id, action ->
        val entry = (mine as? DataResult.Success)?.value?.firstOrNull { it.id == id }
        val names = (lots as? DataResult.Success)?.value.orEmpty().associate { it.id to it.name }
        WaitlistOfferState(
            loading = false,
            entry = entry,
            parkingLotName = entry?.let { names[it.parkingLotId] }.orEmpty(),
            working = action.working,
            rejection = action.rejection,
            failure = (mine as? DataResult.Failure)?.reason ?: action.failure,
            confirmation = action.confirmation,
            declined = action.declined,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaitlistOfferState())

    fun load(entryId: String) {
        this.entryId.value = entryId
    }

    /** La hora de verdad, para la cuenta regresiva. */
    fun now(): Instant = clock.instant()

    fun dismissError() = action.update { it.copy(rejection = null, failure = null) }

    fun accept() = act { entry ->
        when (val outcome = acceptOffer(entry)) {
            is ReservationOutcome.Success -> ActionState(confirmation = outcome.value)
            is ReservationOutcome.Rejected -> ActionState(rejection = outcome.reason)
            is ReservationOutcome.Failed -> ActionState(failure = outcome.reason)
        }
    }

    fun decline() = act { entry ->
        when (val outcome = declineOffer(entry)) {
            is ReservationOutcome.Success -> ActionState(declined = true)
            is ReservationOutcome.Rejected -> ActionState(rejection = outcome.reason)
            is ReservationOutcome.Failed -> ActionState(failure = outcome.reason)
        }
    }

    /** Aceptar y rechazar: una sola vez, y nada más después de haber contestado. */
    private fun act(block: suspend (WaitlistEntry) -> ActionState) {
        val current = state.value
        val entry = current.entry ?: return
        if (current.working || current.confirmation != null || current.declined) return
        viewModelScope.launch {
            action.update { it.copy(working = true, rejection = null, failure = null) }
            action.value = block(entry)
        }
    }

    private data class ActionState(
        val working: Boolean = false,
        val rejection: ReservationRejection? = null,
        val failure: DataErrorReason? = null,
        val confirmation: ReservationConfirmation? = null,
        val declined: Boolean = false,
    )
}
