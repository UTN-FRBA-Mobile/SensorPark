package com.sensorpark.app.fcm

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Se cuelga de la parte autenticada de la app: pide permiso de notificaciones y registra
 * el token al arrancar con sesión (no alcanza con `onNewToken`).
 */
@Composable
fun RegistrarTokenFcm(registrar: () -> Unit) {
    val context = LocalContext.current
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        Notificaciones.crearCanales(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        registrar()
    }
}
