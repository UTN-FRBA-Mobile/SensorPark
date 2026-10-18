package com.sensorpark.domain.notification

import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * La versión falsa de [NotificationRepository] que pide T04: en vez de publicar en la barra de
 * estado guarda los avisos en memoria. Sirve para los tests y para probar la app mientras T17
 * (las pushes del backend) no esté lista. Va en `domain` para que la usen todos los módulos.
 */
class InMemoryNotificationRepository : NotificationRepository {
    private val _shown = MutableStateFlow<List<AppNotification>>(emptyList())

    /** Los avisos visibles, en el orden en que se mostraron. */
    val shown: StateFlow<List<AppNotification>> = _shown.asStateFlow()

    override suspend fun notify(notification: AppNotification) {
        // Como en Android: una clave repetida reemplaza el aviso anterior.
        _shown.update { current -> current.filterNot { it.key == notification.key } + notification }
    }

    override suspend fun cancel(key: String) {
        _shown.update { current -> current.filterNot { it.key == key } }
    }
}
