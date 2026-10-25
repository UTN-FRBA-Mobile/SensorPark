package com.sensorpark.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.DaySummary
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.hourlyOccupancy
import com.sensorpark.domain.repository.ParkingLotAdminRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import com.sensorpark.domain.reservation.ReservationRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Qué pasó con la última acción sobre un lugar. */
data class SpotAction(
    val working: Boolean = false,
    val rejection: ReservationRejection? = null,
    val failure: DataErrorReason? = null,
)

/**
 * Panel de un estacionamiento para su administrador (T22): los lugares en vivo para editarlos, el
 * resumen del día y, aparte, sus datos ([ParkingLotFormViewModel]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParkingLotPanelViewModel @Inject constructor(
    private val repository: ParkingLotRepository,
    private val spotsRepository: SpotAvailabilityRepository,
    private val admin: ParkingLotAdminRepository,
    private val clock: Clock,
) : ViewModel() {
    // El id llega desde la pantalla; un reintento lo vuelve a emitir para suscribirse de nuevo.
    private data class Request(val id: String, val attempt: Int = 0)

    private val request = MutableStateFlow<Request?>(null)

    /** null mientras carga. */
    val result: StateFlow<DataResult<ParkingLot>?> = request.filterNotNull()
        .flatMapLatest { repository.parkingLot(it.id).onStart<DataResult<ParkingLot>?> { emit(null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Los lugares, por código. null mientras cargan. */
    val spots: StateFlow<List<Spot>?> = request.filterNotNull()
        .flatMapLatest { request ->
            spotsRepository.observeSpots(request.id)
                .map<DataResult<SpotAvailability>, List<Spot>?> { result ->
                    (result as? DataResult.Success)?.value?.spots.orEmpty().sortedBy { it.code }
                }
                .onStart { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Las reservas de hoy (desde las 0 hasta las 24, hora argentina). */
    private val today: StateFlow<List<Reservation>> = request.filterNotNull()
        .flatMapLatest { request ->
            val start = clock.instant().atZone(ReservationRules.ZONE).toLocalDate().atStartOfDay(ReservationRules.ZONE)
            admin.reservationsBetween(request.id, start.toInstant(), start.plusDays(1).toInstant())
                .map { (it as? DataResult.Success)?.value.orEmpty() }
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<DaySummary> = combine(spots, today) { spots, today ->
        DaySummary.of(spots.orEmpty(), today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaySummary.of(emptyList(), emptyList()))

    val todayReservations: StateFlow<List<Reservation>> = today

    /** Cuántas reservas cubren cada hora de hoy, de 0 a 23: el gráfico de la solapa Hoy. */
    val hourly: StateFlow<List<Int>> = today
        .map { hourlyOccupancy(it, clock.instant().atZone(ReservationRules.ZONE).toLocalDate(), ReservationRules.ZONE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(24) { 0 })

    private val _action = MutableStateFlow(SpotAction())
    val action: StateFlow<SpotAction> = _action.asStateFlow()

    fun load(id: String) = request.update { if (it?.id == id) it else Request(id) }

    fun retry() = request.update { it?.copy(attempt = it.attempt + 1) }

    fun dismissError() = _action.update { SpotAction() }

    /** Agrega un lugar o cambia el tipo de uno. [onSaved] cierra el diálogo cuando se guardó. */
    fun saveSpot(draft: SpotDraft, onSaved: () -> Unit) = act {
        val id = request.value?.id ?: return@act SpotAction()
        when (val result = admin.saveSpot(id, draft)) {
            is DataResult.Success -> SpotAction().also { onSaved() }
            is DataResult.Failure -> SpotAction(failure = result.reason)
        }
    }

    fun toggleService(spot: Spot) = act {
        val id = request.value?.id ?: return@act SpotAction()
        outcome(admin.setSpotInService(id, spot.id, inService = spot.status == SpotStatus.OUT_OF_SERVICE))
    }

    fun delete(spot: Spot) = act {
        val id = request.value?.id ?: return@act SpotAction()
        outcome(admin.deleteSpot(id, spot.id))
    }

    private fun outcome(result: ReservationOutcome<Unit>) = when (result) {
        is ReservationOutcome.Success -> SpotAction()
        is ReservationOutcome.Rejected -> SpotAction(rejection = result.reason)
        is ReservationOutcome.Failed -> SpotAction(failure = result.reason)
    }

    private fun act(block: suspend () -> SpotAction) {
        if (_action.value.working) return
        viewModelScope.launch {
            _action.value = SpotAction(working = true)
            _action.value = block()
        }
    }
}
