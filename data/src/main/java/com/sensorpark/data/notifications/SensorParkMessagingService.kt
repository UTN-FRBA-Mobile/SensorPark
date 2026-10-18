package com.sensorpark.data.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sensorpark.data.R
import com.sensorpark.data.firebase.DeviceRepositoryFirebase
import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.navigation.DeepLinks
import com.sensorpark.domain.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe pushes sólo `data` (con bloque `notification`, onMessageReceived no corre en segundo
 * plano). El backend manda `type`, `id`, `title` y `body`, todos strings. El `type` decide el
 * canal y a qué pantalla lleva el toque.
 */
@AndroidEntryPoint
class SensorParkMessagingService : FirebaseMessagingService() {
    @Inject lateinit var deviceRepository: DeviceRepositoryFirebase

    @Inject lateinit var notifications: NotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { deviceRepository.saveToken(token) }
                .onFailure { Log.w(TAG, "No se pudo guardar el token FCM", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val rawType = data["type"] ?: return
        // Un tipo que esta versión de la app no conoce se muestra como aviso de reserva.
        val type = NotificationType.fromPushType(rawType) ?: NotificationType.RESERVATION
        val id = data["id"].orEmpty()
        scope.launch {
            notifications.notify(
                AppNotification(
                    type = type,
                    key = rawType + id,
                    title = data["title"] ?: getString(R.string.notification_default_title),
                    body = data["body"].orEmpty(),
                    deepLink = DeepLinks.forNotification(type, id),
                ),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SensorParkFcm"
    }
}
