package com.sensorpark.app.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sensorpark.app.R
import com.sensorpark.app.data.UsuarioRepository
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
    @Inject lateinit var usuarioRepository: UsuarioRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { usuarioRepository.guardarTokenFcm(token) }
                .onFailure { Log.w(TAG, "No se pudo guardar el token FCM", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val datos = message.data
        val tipo = datos["tipo"] ?: return
        // Acá se bifurca por `tipo` cuando haya avisos que necesiten un tratamiento distinto.
        Notificaciones.mostrar(
            context = this,
            clave = tipo + datos["id"].orEmpty(),
            titulo = datos["titulo"] ?: getString(R.string.app_name),
            cuerpo = datos["cuerpo"].orEmpty(),
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
