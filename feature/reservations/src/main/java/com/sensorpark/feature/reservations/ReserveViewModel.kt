package com.sensorpark.feature.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.UserRepository
import com.sensorpark.domain.reservation.ReservationRules
import com.sensorpark.domain.usecase.CreateReservationUseCase
import com.sensorpark.domain.usecase.JoinWaitlistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Lo que el conductor eligió en el formulario. */
data class ReserveForm(
    val date: LocalDate,
    val time: LocalTime,
    val durationMinutes: Int = 60,
    val spotType: SpotType = SpotType.STANDARD,
)

/** El lugar puntual que el conductor tocó en el plano del estacionamiento (T12). */
data class PickedSpot(val id: String, val code: String, val type: SpotType)

data class ReserveState(
    val loading: Boolean = true,
    val parkingLot: ParkingLot? = null,
    val category: Category = Category.STANDARD,
    val form: ReserveForm,
    val working: Boolean = false,
    val rejection: ReservationRejection? = null,
    val failure: DataErrorReason? = null,
    /** Cuando está, la reserva se hizo y la pantalla muestra el resumen. */
    val confirmation: ReservationConfirmation? = null,
    /** Cuando está, se anotó en la lista de espera de esta franja (T18). */
    val waitlist: WaitlistPosition? = null,
    /** El lugar elegido en el plano; sin él se pide un tipo de lugar. */
    val picked: PickedSpot? = null,
) {
    /** El tipo que se reserva: el del lugar elegido, o el de los chips. */
    val spotType: SpotType get() = picked?.type ?: form.spotType

    /** Sin lugar para la franja, se puede anotar en la lista de espera en vez de rendirse. */
    val canJoinWaitlist: Boolean get() = rejection == ReservationRejection.NO_SPOTS && waitlist == null

    val durations: List<Int> get() = ReservationRules.ALLOWED_DURATIONS_MINUTES

    /** Los preferenciales sólo se ofrecen a quien declaró una prioridad. */
    val spotTypes: List<SpotType>
        get() = SpotType.entries.filter { it != SpotType.PRIORITY || category != Category.STANDARD }

    val arrivesAt: Instant
        get() = form.date.atTime(form.time).atZone(ReservationRules.ZONE).toInstant()

    val endsAt: Instant get() = ReservationRules.endsAt(arrivesAt, form.durationMinutes)

    /** Tarifa por hora × duración. Es informativa: la app no cobra. */
    val estimatedAmount: Int
        get() = ReservationRules.amount(parkingLot?.hourlyRate ?: 0, form.durationMinutes)

    /** Lugares libres de ese tipo ahora mismo. No garantiza la franja: eso lo decide el backend. */
    val freeNow: Int get() = parkingLot?.freeSpotsOf(spotType) ?: 0

    val capacity: Int get() = parkingLot?.capacityOf(spotType) ?: 0

    /** Sin lugar no se ofrece un error seco: se sugiere qué probar. Ver [ReserveAlternative]. */
    val alternatives: List<ReserveAlternative>
        get() = if (rejection != ReservationRejection.NO_SPOTS) emptyList() else buildList {
            val shorter = durations.lastOrNull { it < form.durationMinutes }
            if (shorter != null) add(ReserveAlternative.ShorterDuration(shorter))
            add(ReserveAlternative.AnotherTime)
            add(ReserveAlternative.AnotherParkingLot)
        }
}

/** Qué ofrecerle al conductor cuando no hay lugar. */
sealed interface ReserveAlternative {
    data class ShorterDuration(val minutes: Int) : ReserveAlternative

    data object AnotherTime : ReserveAlternative

    data object AnotherParkingLot : ReserveAlternative
}

/**
 * Pantalla "Reservar" (CU-04). Valida con las reglas del dominio antes de llamar al backend, que
 * es el que decide la disponibilidad y tiene la última palabra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReserveViewModel @Inject constructor(
    private val parkingLots: ParkingLotRepository,
    private val users: UserRepository,
    private val createReservation: CreateReservationUseCase,
    private val enterWaitlist: JoinWaitlistUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val parkingLotId = MutableStateFlow<String?>(null)
    private val form = MutableStateFlow(defaultForm())
    private val action = MutableStateFlow(ActionState())
    private val picked = MutableStateFlow<PickedSpot?>(null)

    val state: StateFlow<ReserveState> = combine(
        parkingLotId.filterNotNull().flatMapLatest { parkingLots.parkingLot(it) },
        users.category.catch { emit(Category.STANDARD) },
        form,
        action,
        picked,
    ) { lot, category, form, action, picked ->
        ReserveState(
            loading = false,
            parkingLot = (lot as? DataResult.Success)?.value,
            category = category,
            form = form,
            working = action.working,
            rejection = action.rejection,
            failure = (lot as? DataResult.Failure)?.reason ?: action.failure,
            confirmation = action.confirmation,
            waitlist = action.waitlist,
            picked = picked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReserveState(form = defaultForm()))

    /** [spot] es el lugar elegido en el plano, si vino de ahí. */
    fun load(parkingLotId: String, spot: PickedSpot? = null) {
        this.parkingLotId.value = parkingLotId
        picked.value = spot
    }

    /** Dejar de pedir ese lugar puntual y volver a elegir por tipo. */
    fun clearPickedSpot() {
        picked.value = null
    }

    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun setTime(time: LocalTime) = form.update { it.copy(time = time) }

    fun setDuration(minutes: Int) = form.update { it.copy(durationMinutes = minutes) }

    fun setSpotType(type: SpotType) {
        picked.value = null
        form.update { it.copy(spotType = type) }
    }

    fun dismissError() = action.update { it.copy(rejection = null, failure = null) }

    fun confirm() {
        val current = state.value
        val lot = current.parkingLot ?: return
        // La primera llamada puede tardar: no se deja confirmar dos veces.
        if (current.working || current.confirmation != null) return

        viewModelScope.launch {
            action.update { it.copy(working = true, rejection = null, failure = null) }
            when (val outcome = createReservation(request(current, lot), lot, current.category)) {
                is ReservationOutcome.Success ->
                    action.update { it.copy(working = false, confirmation = outcome.value) }
                is ReservationOutcome.Rejected ->
                    action.update { it.copy(working = false, rejection = outcome.reason) }
                is ReservationOutcome.Failed ->
                    action.update { it.copy(working = false, failure = outcome.reason) }
            }
        }
    }

    /**
     * Sin lugar para la franja: anotarse en la lista de espera con el mismo pedido. Si se libera un
     * lugar, el backend se lo ofrece por unos minutos y avisa (T18).
     */
    fun joinWaitlist() {
        val current = state.value
        val lot = current.parkingLot ?: return
        if (current.working || current.waitlist != null) return

        viewModelScope.launch {
            action.update { it.copy(working = true, failure = null) }
            when (val outcome = enterWaitlist(request(current, lot), lot, current.category)) {
                is ReservationOutcome.Success ->
                    action.update { it.copy(working = false, rejection = null, waitlist = outcome.value) }
                is ReservationOutcome.Rejected ->
                    action.update { it.copy(working = false, rejection = outcome.reason) }
                is ReservationOutcome.Failed ->
                    action.update { it.copy(working = false, failure = outcome.reason) }
            }
        }
    }

    private fun request(current: ReserveState, lot: ParkingLot) = ReservationRequest(
        parkingLotId = lot.id,
        arrivesAt = current.arrivesAt,
        durationMinutes = current.form.durationMinutes,
        spotType = current.spotType,
        spotId = current.picked?.id,
    )

    /** Por defecto, llegar en media hora: la hora más común y siempre dentro de lo permitido. */
    private fun defaultForm(): ReserveForm {
        val start = clock.instant().plusSeconds(30 * 60).atZone(ReservationRules.ZONE)
        return ReserveForm(date = start.toLocalDate(), time = start.toLocalTime().withSecond(0).withNano(0))
    }

    private data class ActionState(
        val working: Boolean = false,
        val rejection: ReservationRejection? = null,
        val failure: DataErrorReason? = null,
        val confirmation: ReservationConfirmation? = null,
        val waitlist: WaitlistPosition? = null,
    )
}
