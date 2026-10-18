package com.sensorpark.app.ui.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.NotificationPreferences
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.repository.NotificationPreferencesRepository
import com.sensorpark.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val notificationPreferences: NotificationPreferencesRepository,
) : ViewModel() {

    /** null mientras se lee el perfil. */
    val category: StateFlow<Category?> = userRepository.category
        .catch { Log.w(TAG, "No se pudo leer la categoría", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Qué avisos quiere recibir. Arrancan todos encendidos. */
    val notifications: StateFlow<NotificationPreferences> = notificationPreferences.preferences
        .catch { Log.w(TAG, "No se pudieron leer las preferencias de avisos", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPreferences())

    fun setCategory(category: Category) {
        viewModelScope.launch {
            runCatching { userRepository.setCategory(category) }
                .onFailure { Log.w(TAG, "No se pudo guardar la categoría", it) }
        }
    }

    fun setNotificationEnabled(type: NotificationType, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { notificationPreferences.setEnabled(type, enabled) }
                .onFailure { Log.w(TAG, "No se pudo guardar la preferencia de avisos", it) }
        }
    }

    private companion object {
        const val TAG = "SensorParkAccount"
    }
}
