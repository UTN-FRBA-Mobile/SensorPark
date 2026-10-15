package com.sensorpark.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    /** null mientras se lee el perfil. */
    val category: StateFlow<Category?> = userRepository.category
        .catch { Log.w(TAG, "No se pudo leer la categoría", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCategory(category: Category) {
        viewModelScope.launch {
            runCatching { userRepository.setCategory(category) }
                .onFailure { Log.w(TAG, "No se pudo guardar la categoría", it) }
        }
    }

    private companion object {
        const val TAG = "SensorParkSettings"
    }
}
