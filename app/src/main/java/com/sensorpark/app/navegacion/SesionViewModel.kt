package com.sensorpark.app.navegacion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.EstadoSesion
import com.sensorpark.domain.repository.AuthRepository
import com.sensorpark.domain.repository.DispositivoRepository
import com.sensorpark.domain.usecase.ObservarEstadoSesionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SesionViewModel @Inject constructor(
    observarEstadoSesion: ObservarEstadoSesionUseCase,
    private val authRepository: AuthRepository,
    private val dispositivoRepository: DispositivoRepository,
) : ViewModel() {

    /** null mientras todavía no se sabe si hay sesión. */
    val estado: StateFlow<EstadoSesion?> = observarEstadoSesion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun registrarDispositivo() {
        viewModelScope.launch {
            runCatching { dispositivoRepository.registrarTokenActual() }
                .onFailure { Log.w("SensorParkFcm", "No se pudo registrar el token FCM", it) }
        }
    }

    fun salir() = authRepository.salir()
}
