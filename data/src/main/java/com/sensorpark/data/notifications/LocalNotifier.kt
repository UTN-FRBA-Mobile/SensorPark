package com.sensorpark.data.notifications

import android.Manifest
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
import com.sensorpark.domain.model.AppNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica un [AppNotification] en la barra de estado: elige el canal según el tipo y arma el toque
 * y los botones a partir de sus deep links. Si falta el permiso no muestra nada y avisa al que
 * llama, que lo resuelve mostrándolo dentro de la app.
 */
@Singleton
class LocalNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Desde Android 13 el permiso lo da el usuario; antes alcanzaba con declararlo. */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun createChannels() = NotificationChannels.createAll(context)

    /** @return false si no se pudo mostrar por falta de permiso. */
    fun show(notification: AppNotification): Boolean {
        if (!hasPermission()) return false
        NotificationChannels.createAll(context)

        val builder = NotificationCompat.Builder(context, NotificationChannels.idOf(notification.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(pendingIntent(notification.deepLink, notification.key.hashCode()))
            .setOngoing(notification.ongoing)
            .setAutoCancel(!notification.ongoing)

        notification.actions.forEachIndexed { index, action ->
            builder.addAction(0, action.label, pendingIntent(action.deepLink, notification.key.hashCode() + index + 1))
        }
        NotificationManagerCompat.from(context).notify(notification.key.hashCode(), builder.build())
        return true
    }

    fun cancel(key: String) = NotificationManagerCompat.from(context).cancel(key.hashCode())

    /** Sin deep link, el toque abre la app en su inicio. */
    private fun pendingIntent(deepLink: String?, requestCode: Int): PendingIntent? {
        val intent = if (deepLink != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(context.packageName)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        } ?: return null
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
