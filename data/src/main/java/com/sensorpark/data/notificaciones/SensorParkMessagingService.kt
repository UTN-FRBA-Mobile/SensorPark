package com.sensorpark.data.notificaciones

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sensorpark.data.R
import com.sensorpark.data.firebase.DispositivoRepositoryFirebase
import com.sensorpark.domain.navegacion.Enlaces
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe pushes sólo `data` (con bloque `notification`, onMessageReceived no corre en
 * segundo plano). El backend manda `tipo`, `id`, `titulo` y `cuerpo`, todos strings.
 */
@AndroidEntryPoint
class SensorParkMessagingService : FirebaseMessagingService() {
    @Inject lateinit var dispositivoRepository: DispositivoRepositoryFirebase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { dispositivoRepository.guardarToken(token) }
                .onFailure { Log.w(TAG, "No se pudo guardar el token FCM", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val datos = message.data
        val tipo = datos["tipo"] ?: return
        val id = datos["id"].orEmpty()
        Notificaciones.mostrar(
            context = this,
            clave = tipo + id,
            titulo = datos["titulo"] ?: getString(R.string.notificacion_titulo_default),
            cuerpo = datos["cuerpo"].orEmpty(),
            // Los avisos sobre una reserva abren esa reserva.
            enlace = if (tipo.startsWith("reserva") && id.isNotEmpty()) Enlaces.reserva(id) else null,
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
