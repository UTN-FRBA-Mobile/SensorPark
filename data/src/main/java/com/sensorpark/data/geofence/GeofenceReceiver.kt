package com.sensorpark.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * El sistema avisa acá cuando el teléfono entra al círculo de una reserva. Muestra el aviso de
 * llegada con el botón que abre el check-in, usando los datos guardados en el teléfono: no hace
 * falta red ni sesión.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {
    @Inject lateinit var geofences: GeofenceRepository

    @Inject lateinit var notifications: NotificationRepository

    @Inject lateinit var messages: ReservationAlertMessages

    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geocerca con error: ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val reservationIds = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (reservationIds.isEmpty()) return

        // El receiver termina apenas vuelve onReceive: goAsync lo mantiene vivo mientras avisa.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reservationIds.forEach { id ->
                    val geofence = geofences.find(id) ?: return@forEach
                    // Con una reserva para más tarde, pasar por el estacionamiento no es llegar.
                    if (!geofence.announcesArrival(clock.instant())) return@forEach
                    notifications.notify(messages.arrival(geofence))
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo mostrar el aviso de llegada", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SensorParkGeofence"
    }
}
