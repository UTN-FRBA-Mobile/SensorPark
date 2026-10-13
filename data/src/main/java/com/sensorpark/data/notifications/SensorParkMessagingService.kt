package com.sensorpark.data.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sensorpark.data.R
import com.sensorpark.data.firebase.DeviceRepositoryFirebase
import com.sensorpark.domain.navigation.DeepLinks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe pushes sólo `data` (con bloque `notification`, onMessageReceived no corre en
 * segundo plano). El backend manda `type`, `id`, `title` y `body`, todos strings.
 */
@AndroidEntryPoint
class SensorParkMessagingService : FirebaseMessagingService() {
    @Inject lateinit var deviceRepository: DeviceRepositoryFirebase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { deviceRepository.saveToken(token) }
                .onFailure { Log.w(TAG, "No se pudo guardar el token FCM", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val id = data["id"].orEmpty()
        Notifications.show(
            context = this,
            key = type + id,
            title = data["title"] ?: getString(R.string.notification_default_title),
            body = data["body"].orEmpty(),
            // Los avisos sobre una reserva abren esa reserva.
            deepLink = if (type.startsWith("reservation") && id.isNotEmpty()) DeepLinks.reservation(id) else null,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SensorParkFcm"
    }
}
