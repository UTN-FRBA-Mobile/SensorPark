package com.sensorpark.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.repository.LocationRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué pasó con el permiso de ubicación. */
enum class LocationAccess {
    /** Todavía no se le preguntó. */
    UNKNOWN,
    GRANTED,

    /** Lo negó, pero se le puede volver a pedir. */
    DENIED,

    /** Lo negó y marcó que no se lo vuelvan a pedir: sólo se puede dar desde los ajustes. */
    DENIED_FOREVER,
}

/** Desde dónde se busca: la ubicación del teléfono o un punto del mapa que eligió el usuario. */
data class SearchOrigin(val coordinates: Coordinates, val fromUser: Boolean)

enum class ViewMode { MAP, LIST }

/**
 * Pestaña Buscar (CU-02): estacionamientos a menos de [RADIUS_METERS] de la ubicación del teléfono.
 *
 * Sin permiso de ubicación no queda en blanco: busca desde una zona por defecto, y el usuario
 * puede mover el mapa y buscar en otra zona.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NearbyParkingLotsViewModel @Inject constructor(
    search: SearchNearbyParkingLotsUseCase,
    private val location: LocationRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(ParkingLotFilter())
    val filter: StateFlow<ParkingLotFilter> = _filter.asStateFlow()

    private val _origin = MutableStateFlow(SearchOrigin(DEFAULT_ORIGIN, fromUser = false))
    val origin: StateFlow<SearchOrigin> = _origin.asStateFlow()

    private val _access = MutableStateFlow(
        if (location.hasLocationPermission()) LocationAccess.GRANTED else LocationAccess.UNKNOWN,
    )
    val access: StateFlow<LocationAccess> = _access.asStateFlow()

    /** Hay una lectura de ubicación en curso. */
    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _mode = MutableStateFlow(ViewMode.MAP)
    val mode: StateFlow<ViewMode> = _mode.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)

    // Cada reintento vuelve a suscribirse: después de un error el flow del repositorio termina.
    private val retries = MutableStateFlow(0)

    /**
     * null sólo mientras carga por primera vez. Cuando cambia desde dónde se busca (llegó la
     * ubicación, o se buscó en otra zona) se sigue mostrando el resultado anterior hasta que llega
     * el nuevo: si pasara por null, la pantalla tiraría abajo el mapa y lo volvería a armar.
     */
    val result: StateFlow<DataResult<List<NearbyParkingLot>>?> =
        combine(_origin, _filter, retries) { origin, filter, _ -> origin to filter }
            .flatMapLatest { (origin, filter) -> search(origin.coordinates, RADIUS_METERS, filter) }
            .onStart<DataResult<List<NearbyParkingLot>>?> { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** El estacionamiento cuyo marcador tocó el usuario; desaparece si los filtros lo sacan. */
    val selected: StateFlow<NearbyParkingLot?> =
        combine(result, _selectedId) { result, id ->
            if (id == null) null else (result as? DataResult.Success)?.value?.firstOrNull { it.parkingLot.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Si el permiso ya estaba dado de antes, no hace falta preguntar nada: se busca de una.
        if (_access.value == LocationAccess.GRANTED) locate()
    }

    /**
     * Lo que respondió el usuario al pedido de permiso.
     * @param canAskAgain false si además marcó que no se lo vuelvan a pedir.
     */
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        _access.value = when {
            granted -> LocationAccess.GRANTED
            canAskAgain -> LocationAccess.DENIED
            else -> LocationAccess.DENIED_FOREVER
        }
        if (granted) locate()
    }

    /** Busca desde donde está el teléfono. Una lectura puntual: no hace falta GPS continuo. */
    fun locate() {
        if (_locating.value) return
        viewModelScope.launch {
            _locating.value = true
            val reading = location.approximateLocation()
            _locating.value = false
            if (reading != null) _origin.value = SearchOrigin(reading.coordinates, fromUser = true)
        }
    }

    /** Busca alrededor del centro del mapa, para quien no dio permiso o quiere mirar otra zona. */
    fun searchHere(center: Coordinates) {
        _origin.value = SearchOrigin(center, fromUser = false)
        _selectedId.value = null
    }

    fun toggleMode() = _mode.update { if (it == ViewMode.MAP) ViewMode.LIST else ViewMode.MAP }

    fun select(parkingLotId: String?) {
        _selectedId.value = parkingLotId
    }

    fun toggleOnlyWithFreeSpots() = _filter.update { it.copy(onlyWithFreeSpots = !it.onlyWithFreeSpots) }

    /** Elegir el tipo que ya estaba elegido lo saca. */
    fun selectSpotType(type: SpotType) = _filter.update { it.copy(spotType = type.takeIf { t -> t != it.spotType }) }

    fun retry() = retries.update { it + 1 }

    companion object {
        /** Desde dónde se busca hasta saber la ubicación, o si no hay permiso. */
        const val DEFAULT_ORIGIN_NAME = "UTN Medrano"
        val DEFAULT_ORIGIN = Coordinates(-34.5984, -58.4200)
        const val RADIUS_METERS = SearchNearbyParkingLotsUseCase.DEFAULT_RADIUS_METERS
    }
}
