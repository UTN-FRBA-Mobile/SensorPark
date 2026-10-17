package com.sensorpark.feature.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject

/** Lo que la pantalla muestra de los lugares de un estacionamiento y de su conexión. */
sealed interface SpotAvailabilityState {
    data object Loading : SpotAvailabilityState

    /** Datos del servidor, al día: "En vivo". */
    data class Live(val spots: List<Spot>, val lastUpdate: Instant?) : SpotAvailabilityState

    /** Última copia guardada en el teléfono, sin poder confirmarla: "Sin conexión". */
    data class Offline(val spots: List<Spot>, val lastUpdate: Instant?) : SpotAvailabilityState

    data class Failure(val reason: DataErrorReason) : SpotAvailabilityState
}

internal fun DataResult<SpotAvailability>.toState(): SpotAvailabilityState = when (this) {
    is DataResult.Failure -> SpotAvailabilityState.Failure(reason)
    is DataResult.Success ->
        if (value.isFromCache) {
            SpotAvailabilityState.Offline(value.spots, value.lastUpdate)
        } else {
            SpotAvailabilityState.Live(value.spots, value.lastUpdate)
        }
}

/**
 * Los lugares de un estacionamiento en vivo. Base para el plano de T12.
 *
 * El listener de Firestore sólo está abierto mientras la pantalla recolecta [state]: con
 * `collectAsStateWithLifecycle` se cierra al pasar la app a segundo plano, y acá se espera 5 s
 * antes de soltarlo para no abrirlo y cerrarlo en un giro de pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SpotAvailabilityViewModel @Inject constructor(
    private val repository: SpotAvailabilityRepository,
) : ViewModel() {
    // El id llega desde la pantalla; un reintento lo vuelve a emitir para suscribirse de nuevo.
    private data class Request(val id: String, val attempt: Int = 0)

    private val request = MutableStateFlow<Request?>(null)

    val state: StateFlow<SpotAvailabilityState> = request.filterNotNull()
        .flatMapLatest { current ->
            repository.observeSpots(current.id)
                .map { it.toState() }
                .onStart { emit(SpotAvailabilityState.Loading) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpotAvailabilityState.Loading)

    fun load(parkingLotId: String) = request.update { if (it?.id == parkingLotId) it else Request(parkingLotId) }

    fun retry() = request.update { it?.copy(attempt = it.attempt + 1) }
}
