package com.sensorpark.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.sensorpark.data.R
import com.sensorpark.domain.model.NotificationType

/**
 * Un canal de Android por tipo de aviso, para que el usuario pueda silenciar uno sin perder los
 * demás desde los ajustes del teléfono.
 *
 * Un canal ya creado no se puede reconfigurar: si cambia su importancia o su sonido hay que crear
 * otro con otro id, por eso los ids llevan sufijo de versión.
 */
internal object NotificationChannels {

    fun idOf(type: NotificationType): String = when (type) {
        NotificationType.RESERVATION -> "reservations_v1"
        NotificationType.REMINDER -> "reminders_v1"
        NotificationType.ARRIVAL -> "arrival_v1"
        NotificationType.WAITLIST -> "waitlist_v1"
    }

    /**
     * Crea los cuatro canales. Es idempotente, así que se llama al arrancar la app y también antes
     * de publicar un aviso: el proceso puede arrancar en un receiver, sin pasar por la Application.
     */
    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationType.entries.forEach { type ->
            manager.createNotificationChannel(
                NotificationChannel(idOf(type), context.getString(nameOf(type)), importanceOf(type)).apply {
                    description = context.getString(descriptionOf(type))
                },
            )
        }
    }

    private fun nameOf(type: NotificationType): Int = when (type) {
        NotificationType.RESERVATION -> R.string.channel_reservations_name
        NotificationType.REMINDER -> R.string.channel_reminders_name
        NotificationType.ARRIVAL -> R.string.channel_arrival_name
        NotificationType.WAITLIST -> R.string.channel_waitlist_name
    }

    private fun descriptionOf(type: NotificationType): Int = when (type) {
        NotificationType.RESERVATION -> R.string.channel_reservations_description
        NotificationType.REMINDER -> R.string.channel_reminders_description
        NotificationType.ARRIVAL -> R.string.channel_arrival_description
        NotificationType.WAITLIST -> R.string.channel_waitlist_description
    }

    /** Llegada y lista de espera interrumpen: hay que actuar en el momento. */
    private fun importanceOf(type: NotificationType): Int = when (type) {
        NotificationType.ARRIVAL, NotificationType.WAITLIST -> NotificationManager.IMPORTANCE_HIGH
        NotificationType.RESERVATION, NotificationType.REMINDER -> NotificationManager.IMPORTANCE_DEFAULT
    }
}
