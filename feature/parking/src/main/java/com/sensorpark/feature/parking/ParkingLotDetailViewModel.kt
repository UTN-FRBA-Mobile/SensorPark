package com.sensorpark.feature.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.repository.FavoritesRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParkingLotDetailViewModel @Inject constructor(
    private val repository: ParkingLotRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {
    // El id llega desde la pantalla; un reintento lo vuelve a emitir para suscribirse de nuevo.
    private data class Request(val id: String, val attempt: Int = 0)

    private val request = MutableStateFlow<Request?>(null)
    @Volatile
    private var loadedId: String? = null

    /** null mientras carga. */
    val result: StateFlow<DataResult<ParkingLot>?> = request.filterNotNull()
        .flatMapLatest { req ->
            repository.parkingLot(req.id)
                .onEach { if (it is DataResult.Success) loadedId = req.id }
                .onStart<DataResult<ParkingLot>?> { if (loadedId != req.id) emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Si está en favoritos (T19). Se lee del teléfono, así que anda sin conexión. */
    val isFavorite: StateFlow<Boolean> =
        combine(request.filterNotNull(), favorites.favorites().catch { emit(emptySet()) }) { request, ids ->
            request.id in ids
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun load(id: String) = request.update { if (it?.id == id) it else Request(id) }

    fun retry() {
        loadedId = null
        request.update { it?.copy(attempt = it.attempt + 1) }
    }

    fun toggleFavorite() {
        val id = request.value?.id ?: return
        viewModelScope.launch { favorites.setFavorite(id, !isFavorite.value) }
    }
}
