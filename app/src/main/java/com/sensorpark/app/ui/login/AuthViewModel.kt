package com.sensorpark.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.AuthErrorReason
import com.sensorpark.domain.model.AuthException
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [error] es el motivo; el texto lo elige la pantalla, desde strings.xml. */
data class AuthUiState(val loading: Boolean = false, val error: AuthErrorReason? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) = execute {
        authRepository.signIn(email.trim(), password)
    }

    fun signUp(name: String, email: String, password: String, role: Role, category: Category) = execute {
        authRepository.signUp(name.trim(), email.trim(), password, role, category)
    }

    // Si sale bien no hay nada que navegar: AppNavigation reacciona al cambio de sesión.
    private fun execute(action: suspend () -> Unit) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            _state.value = try {
                action()
                AuthUiState()
            } catch (e: AuthException) {
                AuthUiState(error = e.reason)
            } catch (e: Exception) {
                AuthUiState(error = AuthErrorReason.UNKNOWN)
            }
        }
    }
}
