package com.sensorpark.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.AuthException
import com.sensorpark.domain.model.MotivoErrorAuth
import com.sensorpark.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoAuth(val cargando: Boolean = false, val error: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoAuth())
    val estado: StateFlow<EstadoAuth> = _estado.asStateFlow()

    fun ingresar(email: String, password: String) = ejecutar {
        authRepository.ingresar(email.trim(), password)
    }

    fun registrar(nombre: String, email: String, password: String) = ejecutar {
        authRepository.registrar(nombre.trim(), email.trim(), password)
    }

    // Si sale bien no hay nada que navegar: NavegacionApp reacciona al cambio de sesión.
    private fun ejecutar(accion: suspend () -> Unit) {
        if (_estado.value.cargando) return
        _estado.value = EstadoAuth(cargando = true)
        viewModelScope.launch {
            _estado.value = try {
                accion()
                EstadoAuth()
            } catch (e: AuthException) {
                EstadoAuth(error = mensajeDe(e.motivo))
            } catch (e: Exception) {
                EstadoAuth(error = mensajeDe(MotivoErrorAuth.DESCONOCIDO))
            }
        }
    }

    private fun mensajeDe(motivo: MotivoErrorAuth): String = when (motivo) {
        MotivoErrorAuth.PASSWORD_DEBIL -> "La contraseña debe tener al menos 6 caracteres."
        MotivoErrorAuth.EMAIL_EN_USO -> "Ya existe una cuenta con ese email."
        MotivoErrorAuth.CREDENCIALES_INVALIDAS -> "Email o contraseña incorrectos."
        MotivoErrorAuth.SIN_CONEXION -> "Sin respuesta del servidor. Revisá tu conexión."
        MotivoErrorAuth.DESCONOCIDO -> "No se pudo completar la operación. Intentá de nuevo."
    }
}
