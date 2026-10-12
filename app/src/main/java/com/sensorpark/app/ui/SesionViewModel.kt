package com.sensorpark.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.app.data.AuthRepository
import com.sensorpark.app.data.UsuarioRepository
import com.sensorpark.app.model.Rol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion

    /** @param rol null mientras el backend todavía no asignó el rol de una cuenta recién creada. */
    data class Activa(val email: String, val rol: Rol?) : EstadoSesion
}

@HiltViewModel
class SesionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val usuarioRepository: UsuarioRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val estado: StateFlow<EstadoSesion> = authRepository.sesion
        .flatMapLatest { usuario ->
            if (usuario == null) {
                flowOf(EstadoSesion.SinSesion)
            } else {
                val email = usuario.email.orEmpty()
                authRepository.rol(usuario.uid)
                    .catch { emit(null) }
                    .map<Rol?, EstadoSesion> { EstadoSesion.Activa(email, it) }
                    .onStart { emit(EstadoSesion.Activa(email, rol = null)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoSesion.Cargando)

    fun registrarTokenFcm() {
        viewModelScope.launch {
            runCatching { usuarioRepository.registrarTokenFcmActual() }
                .onFailure { Log.w("SensorParkFcm", "No se pudo registrar el token FCM", it) }
        }
    }

    fun salir() = authRepository.salir()
}
