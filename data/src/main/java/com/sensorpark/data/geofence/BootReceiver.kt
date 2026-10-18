package com.sensorpark.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sensorpark.domain.repository.GeofenceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Al reiniciar el teléfono se borran las geocercas registradas, así que hay que volver a crearlas
 * a partir de la tabla local. No depende de la red ni de que la sesión esté lista.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var geofences: GeofenceRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                geofences.restoreAll()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron volver a registrar las geocercas", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SensorParkGeofence"
    }
}
