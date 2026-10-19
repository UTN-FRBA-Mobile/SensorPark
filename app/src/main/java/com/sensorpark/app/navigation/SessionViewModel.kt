package com.sensorpark.app.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.SessionState
import com.sensorpark.domain.repository.AuthRepository
import com.sensorpark.domain.repository.DeviceRepository
import com.sensorpark.domain.usecase.ObserveSessionStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    observeSessionState: ObserveSessionStateUseCase,
    private val authRepository: AuthRepository,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    /** null mientras todavía no se sabe si hay sesión. */
    val state: StateFlow<SessionState?> = observeSessionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun registerDevice() {
        viewModelScope.launch {
            runCatching { deviceRepository.registerCurrentToken() }
                .onFailure { Log.w("SensorParkFcm", "No se pudo registrar el token FCM", it) }
        }
    }

    /**
     * Antes de cerrar la sesión saca este teléfono de la cuenta, para que no le sigan llegando sus
     * avisos. Sin conexión no espera más de unos segundos: si quedó registrado, el backend lo pasa
     * a la próxima cuenta que inicie sesión acá.
     */
    fun signOut() {
        viewModelScope.launch {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                runCatching { deviceRepository.unregisterCurrentToken() }
                    .onFailure { Log.w("SensorParkFcm", "No se pudo desregistrar el token FCM", it) }
            }
            authRepository.signOut()
        }
    }

    private companion object {
        const val UNREGISTER_TIMEOUT_MS = 3_000L
    }
}
