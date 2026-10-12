package com.sensorpark.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.sensorpark.app.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
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

    // Si sale bien no hay nada que navegar: RaizApp reacciona al cambio de sesión.
    private fun ejecutar(accion: suspend () -> Unit) {
        if (_estado.value.cargando) return
        _estado.value = EstadoAuth(cargando = true)
        viewModelScope.launch {
            _estado.value = try {
                accion()
                EstadoAuth()
            } catch (e: TimeoutCancellationException) {
                EstadoAuth(error = "Sin respuesta del servidor. Revisá tu conexión.")
            } catch (e: Exception) {
                EstadoAuth(error = mensajeDe(e))
            }
        }
    }

    private fun mensajeDe(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres."
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese email."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Email o contraseña incorrectos."
        else -> "No se pudo completar la operación. Intentá de nuevo."
    }
}
