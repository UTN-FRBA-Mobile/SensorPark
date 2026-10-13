package com.sensorpark.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sensorpark.data.R

object Notifications {
    // Un canal ya creado no se puede reconfigurar: si cambia importancia o sonido, subir el sufijo.
    private const val GENERAL_CHANNEL = "general_v1"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            GENERAL_CHANNEL,
            context.getString(R.string.general_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * @param key identifica aquello sobre lo que se avisa (p. ej. tipo + id de reserva):
     * dos avisos con la misma clave se reemplazan en vez de apilarse.
     * @param deepLink enlace que abre el toque; si es null abre la app en su inicio.
     */
    fun show(context: Context, key: String, title: String, body: String, deepLink: String? = null) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        createChannels(context)
        val intent = if (deepLink != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(context.packageName)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        }
        val openApp = PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, GENERAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    }
}
