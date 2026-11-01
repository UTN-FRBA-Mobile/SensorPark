package com.sensorpark.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.map
import com.sensorpark.domain.repository.FavoritesRepository
import com.sensorpark.domain.repository.LocationRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.usecase.ParkingLotFilter
import com.sensorpark.domain.usecase.SearchNearbyParkingLotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del permiso de ubicación. UNKNOWN: todavía no se pidió. */
enum class LocationAccess { UNKNOWN, GRANTED, DENIED }

/** De dónde sale el punto de búsqueda: el teléfono, la zona por defecto o un punto del mapa. */
enum class OriginSource { DEVICE, DEFAULT_ZONE, MAP_AREA }

/** El punto desde el que se buscan los estacionamientos cercanos. */
data class Origin(val coordinates: Coordinates, val source: OriginSource)

enum class ViewMode { MAP, LIST }

/**
 * Pestaña Buscar (CU-02). Busca desde la ubicación del teléfono y, sin permiso, desde una zona por
 * defecto. Los estacionamientos se escuchan una sola vez: filtros, origen y favoritos sólo
 * recalculan distancias en memoria, así cambiar un filtro no reabre el listener.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NearbyParkingLotsViewModel internal constructor(
    private val parkingLots: ParkingLotRepository,
    private val location: LocationRepository,
    private val favoritesRepository: FavoritesRepository,
    /** Dónde se calculan las distancias; los tests pasan uno propio. */
    computation: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        parkingLots: ParkingLotRepository,
        location: LocationRepository,
        favorites: FavoritesRepository,
    ) : this(parkingLots, location, favorites, Dispatchers.Default)

    private val _filter = MutableStateFlow(ParkingLotFilter())
    val filter: StateFlow<ParkingLotFilter> = _filter.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)

    /** Sólo los favoritos, estén a la distancia que estén. */
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.MAP)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _origin = MutableStateFlow(DEFAULT_ORIGIN)
    val origin: StateFlow<Origin> = _origin.asStateFlow()

    private val _access = MutableStateFlow(
        if (location.hasApproximatePermission()) LocationAccess.GRANTED else LocationAccess.UNKNOWN,
    )
    val access: StateFlow<LocationAccess> = _access.asStateFlow()

    private val _locating = MutableStateFlow(false)

    /** Hay una lectura de ubicación en curso. */
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _locationFailed = MutableStateFlow(false)

    /** Hay permiso pero no llegó una lectura (por ejemplo, GPS apagado). */
    val locationFailed: StateFlow<Boolean> = _locationFailed.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)

    /** El marcador tocado en el mapa. */
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    // Cada reintento vuelve a suscribirse: después de un error el flow del repositorio termina.
    private val retries = MutableStateFlow(0)
    @Volatile
    private var hasLoaded = false
    private var locationJob: Job? = null

    /** Los favoritos, del teléfono: se ven también sin conexión. */
    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.favorites()
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    private val allLots: StateFlow<DataResult<List<ParkingLot>>?> = retries
        .flatMapLatest {
            parkingLots.parkingLots()
                .onEach { if (it is DataResult.Success) hasLoaded = true }
                .onStart<DataResult<List<ParkingLot>>?> { if (!hasLoaded) emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** null mientras carga. */
    val result: StateFlow<DataResult<List<NearbyParkingLot>>?> =
        combine(allLots, _origin, _filter, _favoritesOnly, favoriteIds) { lots, origin, filter, onlyFavorites, favorites ->
            val effective = if (onlyFavorites) filter.copy(favoriteIds = favorites) else filter
            lots?.map { SearchNearbyParkingLotsUseCase.nearby(it, origin.coordinates, RADIUS_METERS, effective) }
        }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        if (_access.value == LocationAccess.GRANTED) locate()
    }

    /** El usuario respondió al diálogo del sistema. */
    fun onPermissionResult(granted: Boolean) {
        _access.value = if (granted) LocationAccess.GRANTED else LocationAccess.DENIED
        if (granted) locate() else fallBackToDefaultZone()
    }

    /** Al volver a primer plano: el permiso pudo cambiar en Ajustes. */
    fun onResume() {
        val granted = location.hasApproximatePermission()
        when {
            granted && _access.value != LocationAccess.GRANTED -> {
                _access.value = LocationAccess.GRANTED
                locate()
            }
            !granted && _access.value == LocationAccess.GRANTED -> {
                _access.value = LocationAccess.DENIED
                fallBackToDefaultZone()
            }
        }
    }

    /** Lee la ubicación del teléfono y busca desde ahí. */
    fun locate() {
        if (!location.hasApproximatePermission()) return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            _locating.value = true
            _locationFailed.value = false
            val reading = location.approximateLocation()
            if (reading != null) {
                _origin.value = Origin(reading.coordinates, OriginSource.DEVICE)
            } else {
                _locationFailed.value = true
            }
            _locating.value = false
        }
    }

    /** Busca alrededor de un punto del mapa. */
    fun searchHere(center: Coordinates) {
        _origin.value = Origin(center, OriginSource.MAP_AREA)
        _selectedId.value = null
    }

    fun toggleOnlyWithFreeSpots() = _filter.update { it.copy(onlyWithFreeSpots = !it.onlyWithFreeSpots) }

    /** Elegir el tipo que ya estaba elegido lo saca. */
    fun selectSpotType(type: SpotType) = _filter.update { it.copy(spotType = type.takeIf { t -> t != it.spotType }) }

    fun toggleFavoritesOnly() = _favoritesOnly.update { !it }

    fun toggleFavorite(parkingLotId: String) {
        viewModelScope.launch {
            favoritesRepository.setFavorite(parkingLotId, parkingLotId !in favoriteIds.value)
        }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun select(parkingLotId: String?) {
        _selectedId.value = parkingLotId
    }

    fun retry() {
        hasLoaded = false
        retries.update { it + 1 }
    }

    private fun fallBackToDefaultZone() {
        locationJob?.cancel()
        _locating.value = false
        _locationFailed.value = false
        if (_origin.value.source == OriginSource.DEVICE) _origin.value = DEFAULT_ORIGIN
    }

    companion object {
        const val RADIUS_METERS = SearchNearbyParkingLotsUseCase.DEFAULT_RADIUS_METERS
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Zona por defecto sin ubicación: UTN Medrano. */
        const val DEFAULT_ZONE_NAME = "UTN Medrano"
        val DEFAULT_ORIGIN = Origin(Coordinates(-34.5984, -58.4200), OriginSource.DEFAULT_ZONE)
    }
}
