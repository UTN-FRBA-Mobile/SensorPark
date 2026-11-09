package com.sensorpark.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.DaySummary
import com.sensorpark.domain.model.ParkingLevel
import com.sensorpark.domain.model.ParkingLevelDraft
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotPlanPosition
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.hourlyOccupancy
import com.sensorpark.domain.model.sanitizeSpotPositions
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Qué pasó con la última acción sobre un lugar o sobre el plano. */
data class SpotAction(
    val working: Boolean = false,
    val savedPositions: Boolean = false,
    val rejection: ReservationRejection? = null,
    val failure: DataErrorReason? = null,
    val planFailure: DataErrorReason? = null,
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
    @Volatile
    private var loadedLotId: String? = null
    @Volatile
    private var loadedSpotsId: String? = null

    /** null mientras carga. */
    val result: StateFlow<DataResult<ParkingLot>?> = request.filterNotNull()
        .flatMapLatest { req ->
            repository.parkingLot(req.id)
                .onEach { if (it is DataResult.Success) loadedLotId = req.id }
                .onStart<DataResult<ParkingLot>?> { if (loadedLotId != req.id) emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Los lugares, por código. null mientras cargan. */
    val spots: StateFlow<List<Spot>?> = request.filterNotNull()
        .flatMapLatest { request ->
            spotsRepository.observeSpots(request.id)
                .map<DataResult<SpotAvailability>, List<Spot>?> { result ->
                    (result as? DataResult.Success)?.value?.spots.orEmpty().sortedBy { it.code }
                }
                .onEach { if (it != null) loadedSpotsId = request.id }
                .onStart { if (loadedSpotsId != request.id) emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Los pisos o niveles del plano del estacionamiento, ordenados por `order`. */
    val levels: StateFlow<List<ParkingLevel>> = request.filterNotNull()
        .flatMapLatest { request ->
            spotsRepository.observeLevels(request.id)
                .map { (it as? DataResult.Success)?.value.orEmpty() }
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun retry() {
        loadedLotId = null
        loadedSpotsId = null
        request.update { it?.copy(attempt = it.attempt + 1) }
    }

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

    /** Crea o actualiza el plano del estacionamiento. [onSaved] recibe el id del plano guardado. */
    fun saveLevel(draft: ParkingLevelDraft, onSaved: (String) -> Unit) = act {
        val id = request.value?.id ?: return@act SpotAction()
        when (val result = admin.saveLevel(id, draft)) {
            is DataResult.Success -> SpotAction().also { onSaved(result.value) }
            is DataResult.Failure -> SpotAction(planFailure = result.reason)
        }
    }

    /** Borra el plano y devuelve sus lugares a la lista de pendientes de ubicar. */
    fun deleteLevel(levelId: String, onDeleted: () -> Unit = {}) = act {
        val id = request.value?.id ?: return@act SpotAction()
        when (val result = admin.deleteLevel(id, levelId)) {
            is DataResult.Success -> SpotAction().also { onDeleted() }
            is DataResult.Failure -> SpotAction(planFailure = result.reason)
        }
    }

    /** Guarda en lote las posiciones de los lugares en el plano, validando consistencia 1 a 1. */
    fun saveSpotPositions(positions: Map<String, SpotPlanPosition?>, onSaved: () -> Unit = {}) = act {
        val id = request.value?.id ?: return@act SpotAction()
        val currentSpots = spots.value.orEmpty()
        val currentLevels = levels.value
        val sanitized = sanitizeSpotPositions(currentSpots, currentLevels, positions)
        when (val result = admin.saveSpotPositions(id, sanitized)) {
            is DataResult.Success -> SpotAction(savedPositions = true).also { onSaved() }
            is DataResult.Failure -> SpotAction(planFailure = result.reason)
        }
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
