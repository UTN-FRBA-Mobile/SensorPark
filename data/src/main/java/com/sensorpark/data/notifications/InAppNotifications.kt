package com.sensorpark.data.notifications

import com.sensorpark.domain.model.AppNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los avisos que no pudieron salir por la barra de estado porque el usuario negó el permiso. La
 * app los muestra como banner mientras está abierta, así negar el permiso no hace perder avisos.
 */
@Singleton
class InAppNotifications @Inject constructor() {
    private val _messages = MutableSharedFlow<AppNotification>(extraBufferCapacity = 8)
    val messages: SharedFlow<AppNotification> = _messages.asSharedFlow()

    suspend fun emit(notification: AppNotification) = _messages.emit(notification)
}
