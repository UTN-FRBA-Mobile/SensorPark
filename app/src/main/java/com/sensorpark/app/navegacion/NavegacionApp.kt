package com.sensorpark.app.navegacion

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sensorpark.app.ui.diagnostico.PantallaDiagnostico
import com.sensorpark.app.ui.inicio.PantallaInicio
import com.sensorpark.app.ui.login.PantallaLogin
import com.sensorpark.app.ui.login.PantallaRegistro
import com.sensorpark.app.ui.preferencias.PantallaPreferencias
import com.sensorpark.domain.model.EstadoSesion
import com.sensorpark.domain.navegacion.Enlaces
import com.sensorpark.feature.admin.PantallaAdmin
import com.sensorpark.feature.checkin.PantallaCheckIn
import com.sensorpark.feature.estacionamiento.PantallaDetalleEstacionamiento
import com.sensorpark.feature.mapa.PantallaMapa
import com.sensorpark.feature.reservas.PantallaMisReservas
import com.sensorpark.feature.reservas.PantallaReservar

/** Raíz de la UI: según la sesión muestra el ingreso o la app. */
@Composable
fun NavegacionApp(vm: SesionViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()

    when (val actual = estado) {
        null -> Cargando()
        EstadoSesion.SinSesion -> NavegacionIngreso()
        is EstadoSesion.Activa -> {
            RegistrarDispositivo(vm::registrarDispositivo)
            if (actual.rol == null) {
                Cargando(mensaje = "Preparando tu cuenta…", onSalir = vm::salir)
            } else {
                NavegacionPrincipal(actual, onSalir = vm::salir)
            }
        }
    }
}

@Composable
private fun NavegacionIngreso() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Ruta.Login.patron) {
        composable(Ruta.Login.patron) {
            PantallaLogin(irARegistro = { nav.navigate(Ruta.Registro.patron) })
        }
        composable(Ruta.Registro.patron) {
            PantallaRegistro(volver = { nav.popBackStack() })
        }
    }
}

/**
 * Grafo de la app con sesión. Los features no se conocen entre sí ni conocen las rutas:
 * cada pantalla recibe lambdas y acá se decide a dónde lleva cada una.
 */
@Composable
private fun NavegacionPrincipal(sesion: EstadoSesion.Activa, onSalir: () -> Unit) {
    val nav = rememberNavController()
    AtenderEnlacesNuevos(nav)

    NavHost(navController = nav, startDestination = Ruta.Inicio.patron) {
        composable(Ruta.Inicio.patron) {
            PantallaInicio(sesion = sesion, irA = { nav.navigate(it) })
        }
        composable(Ruta.Mapa.patron) {
            PantallaMapa(irADetalle = { nav.navigate(Ruta.DetalleEstacionamiento.crear(it)) })
        }
        composable(Ruta.DetalleEstacionamiento.patron) { entrada ->
            PantallaDetalleEstacionamiento(
                estacionamientoId = entrada.arguments?.getString(Ruta.ARG_ESTACIONAMIENTO_ID).orEmpty(),
                irAReservar = { nav.navigate(Ruta.Reservar.crear(it)) },
            )
        }
        composable(Ruta.Reservar.patron) { entrada ->
            PantallaReservar(
                estacionamientoId = entrada.arguments?.getString(Ruta.ARG_ESTACIONAMIENTO_ID).orEmpty(),
                irAMisReservas = { nav.navigate(Ruta.MisReservas.crear()) },
            )
        }
        composable(
            route = Ruta.MisReservas.patron,
            arguments = listOf(
                navArgument(Enlaces.ARG_RESERVA_ID) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = Enlaces.RESERVA }),
        ) { entrada ->
            PantallaMisReservas(
                reservaId = entrada.arguments?.getString(Enlaces.ARG_RESERVA_ID),
                irACheckIn = { nav.navigate(Ruta.CheckIn.crear(it)) },
            )
        }
        composable(Ruta.CheckIn.patron) { entrada ->
            PantallaCheckIn(reservaId = entrada.arguments?.getString(Enlaces.ARG_RESERVA_ID).orEmpty())
        }
        composable(Ruta.Preferencias.patron) {
            PantallaPreferencias(onSalir = onSalir)
        }
        composable(Ruta.Admin.patron) {
            PantallaAdmin()
        }
        composable(Ruta.Diagnostico.patron) {
            PantallaDiagnostico()
        }
    }
}

/** Con la app ya abierta, un deep link llega por onNewIntent y hay que pasárselo al NavController. */
@Composable
private fun AtenderEnlacesNuevos(nav: NavHostController) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity, nav) {
        val listener = Consumer<Intent> { nav.handleDeepLink(it) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

/** Pide permiso de notificaciones y registra el token al arrancar con sesión (no alcanza con onNewToken). */
@Composable
private fun RegistrarDispositivo(registrar: () -> Unit) {
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        registrar()
    }
}

@Composable
private fun Cargando(mensaje: String? = null, onSalir: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (mensaje != null) Text(mensaje)
        if (onSalir != null) TextButton(onClick = onSalir) { Text("Cerrar sesión") }
    }
}
