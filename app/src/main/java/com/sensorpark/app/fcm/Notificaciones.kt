package com.sensorpark.app.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sensorpark.app.MainActivity
import com.sensorpark.app.R

object Notificaciones {
    // Un canal ya creado no se puede reconfigurar: si cambia importancia o sonido, subir el sufijo.
    private const val CANAL_GENERAL = "general_v1"

    fun crearCanales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL_GENERAL,
            context.getString(R.string.canal_general),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    /**
     * @param clave identifica aquello sobre lo que se avisa (p. ej. tipo + id de reserva):
     * dos avisos con la misma clave se reemplazan en vez de apilarse.
     */
    fun mostrar(context: Context, clave: String, titulo: String, cuerpo: String) {
        val permitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitido) return

        crearCanales(context)
        val abrirApp = PendingIntent.getActivity(
            context,
            clave.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(context, CANAL_GENERAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrirApp)
            .build()
        NotificationManagerCompat.from(context).notify(clave.hashCode(), notificacion)
    }
}
