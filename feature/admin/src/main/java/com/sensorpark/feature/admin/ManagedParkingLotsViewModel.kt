package com.sensorpark.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.map
import com.sensorpark.domain.repository.ParkingLotRepository
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
import javax.inject.Inject

/** Los estacionamientos que administra el usuario: los que lo tienen en `adminUids`, o todos si es ADMIN. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManagedParkingLotsViewModel @Inject constructor(
    private val repository: ParkingLotRepository,
) : ViewModel() {
    private data class Request(val uid: String, val seesAll: Boolean, val attempt: Int = 0)

    private val request = MutableStateFlow<Request?>(null)

    /** null mientras carga. */
    val result: StateFlow<DataResult<List<ParkingLot>>?> = request.filterNotNull()
        .flatMapLatest { request ->
            repository.parkingLots()
                .map { result ->
                    result.map { lots ->
                        lots.filter { request.seesAll || request.uid in it.adminUids }.sortedBy { it.name }
                    }
                }
                .onStart<DataResult<List<ParkingLot>>?> { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(uid: String, seesAll: Boolean) =
        request.update { if (it?.uid == uid && it.seesAll == seesAll) it else Request(uid, seesAll) }

    fun retry() = request.update { it?.copy(attempt = it.attempt + 1) }
}
