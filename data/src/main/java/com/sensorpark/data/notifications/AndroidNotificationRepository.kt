package com.sensorpark.data.notifications

import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.repository.NotificationPreferencesRepository
import com.sensorpark.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Muestra los avisos respetando lo que el usuario eligió en Cuenta: si apagó ese tipo no se
 * muestra nada, y si no dio el permiso de notificaciones el aviso va como banner dentro de la app.
 */
@Singleton
class AndroidNotificationRepository @Inject constructor(
    private val notifier: LocalNotifier,
    private val preferences: NotificationPreferencesRepository,
    private val inApp: InAppNotifications,
) : NotificationRepository {

    override suspend fun notify(notification: AppNotification) {
        if (!preferences.preferences.first().isEnabled(notification.type)) return
        if (!notifier.show(notification)) inApp.emit(notification)
    }

    override suspend fun cancel(key: String) = notifier.cancel(key)
}
