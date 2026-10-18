package com.sensorpark.app.ui.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.app.R

/**
 * Sin interfaz propia: cuelga de la pantalla principal y se ocupa de dos cosas.
 *
 * 1. Después de la primera reserva explica para qué sirven los avisos y la ubicación, y recién
 *    ahí pide los permisos. Si el usuario los niega, la app sigue funcionando: no hay aviso de
 *    llegada y el check-in se hace a mano.
 * 2. Muestra como banner los avisos que no pudieron salir por la barra de estado.
 */
@Composable
fun AlertsHost(snackbarHostState: SnackbarHostState, vm: AlertsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val shouldAsk by vm.shouldAskPermissions.collectAsStateWithLifecycle()
    var explaining by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.banners.collect { notification ->
            snackbarHostState.showSnackbar("${notification.title}. ${notification.body}")
        }
    }

    val backgroundLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.onPermissionsAsked() }

    val notificationsAndLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        // El permiso de segundo plano se pide aparte y sólo después de tener el común.
        if (fine && needsBackgroundLocation(context)) {
            backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            vm.onPermissionsAsked()
        }
    }

    LaunchedEffect(shouldAsk) { if (shouldAsk) explaining = true }

    if (explaining) {
        AlertDialog(
            onDismissRequest = {
                explaining = false
                vm.onPermissionsAsked()
            },
            title = { Text(stringResource(R.string.permissions_title)) },
            text = { Text(stringResource(R.string.permissions_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        explaining = false
                        notificationsAndLocation.launch(requestedPermissions())
                    },
                ) { Text(stringResource(R.string.permissions_accept)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        explaining = false
                        vm.onPermissionsAsked()
                    },
                ) { Text(stringResource(R.string.permissions_later)) }
            },
        )
    }
}

private fun requestedPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun needsBackgroundLocation(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
