package com.sensorpark.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.usecase.ParkingLotFilter
import com.sensorpark.domain.usecase.SearchNearbyParkingLotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NearbyParkingLotsViewModel @Inject constructor(
    search: SearchNearbyParkingLotsUseCase,
) : ViewModel() {
    private val _filter = MutableStateFlow(ParkingLotFilter())
    val filter: StateFlow<ParkingLotFilter> = _filter.asStateFlow()

    // Cada reintento vuelve a suscribirse: después de un error el flow del repositorio termina.
    private val retries = MutableStateFlow(0)

    /** null mientras carga. */
    val result: StateFlow<DataResult<List<NearbyParkingLot>>?> =
        combine(_filter, retries) { filter, _ -> filter }
            .flatMapLatest { filter -> search(ORIGIN, RADIUS_METERS, filter).onStart<DataResult<List<NearbyParkingLot>>?> { emit(null) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleOnlyWithFreeSpots() = _filter.update { it.copy(onlyWithFreeSpots = !it.onlyWithFreeSpots) }

    /** Elegir el tipo que ya estaba elegido lo saca. */
    fun selectSpotType(type: SpotType) = _filter.update { it.copy(spotType = type.takeIf { t -> t != it.spotType }) }

    fun retry() = retries.update { it + 1 }

    companion object {
        // Punto fijo hasta que T11 use la ubicación del teléfono.
        const val ORIGIN_NAME = "UTN Medrano"
        val ORIGIN = Coordinates(-34.5984, -58.4200)
        const val RADIUS_METERS = SearchNearbyParkingLotsUseCase.DEFAULT_RADIUS_METERS
    }
}
