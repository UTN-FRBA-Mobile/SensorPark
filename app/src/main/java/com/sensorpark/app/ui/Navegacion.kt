package com.sensorpark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sensorpark.app.fcm.RegistrarTokenFcm
import com.sensorpark.app.model.Rol
import com.sensorpark.app.ui.auth.PantallaLogin
import com.sensorpark.app.ui.auth.PantallaRegistro
import com.sensorpark.app.ui.inicio.PantallaInicio

sealed class Pantalla(val ruta: String) {
    data object Login : Pantalla("login")
    data object Registro : Pantalla("registro")
    data object InicioConductor : Pantalla("conductor/inicio")
    data object InicioAdminEstacionamiento : Pantalla("admin-estacionamiento/inicio")
    data object InicioAdmin : Pantalla("admin/inicio")
}

/** Mira el estado de la sesión y elige entre el flujo de ingreso y la app autenticada. */
@Composable
fun RaizApp(vm: SesionViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()

    when (val actual = estado) {
        EstadoSesion.Cargando -> Cargando()
        EstadoSesion.SinSesion -> FlujoIngreso()
        is EstadoSesion.Activa -> AppAutenticada(actual, vm)
    }
}

@Composable
private fun FlujoIngreso() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Pantalla.Login.ruta) {
        composable(Pantalla.Login.ruta) {
            PantallaLogin(irARegistro = { nav.navigate(Pantalla.Registro.ruta) })
        }
        composable(Pantalla.Registro.ruta) {
            PantallaRegistro(volver = { nav.popBackStack() })
        }
    }
}

/** Todo lo que necesita sesión cuelga de acá: token FCM, listeners globales y la navegación por rol. */
@Composable
private fun AppAutenticada(sesion: EstadoSesion.Activa, vm: SesionViewModel) {
    RegistrarTokenFcm(registrar = vm::registrarTokenFcm)

    when (val rol = sesion.rol) {
        null -> Cargando(mensaje = "Preparando tu cuenta…", onSalir = vm::salir)
        // key: si el backend cambia el rol, se arma una navegación nueva desde su inicio.
        else -> key(rol) { NavegacionPorRol(rol, sesion.email, vm::salir) }
    }
}

@Composable
private fun NavegacionPorRol(rol: Rol, email: String, onSalir: () -> Unit) {
    val nav = rememberNavController()
    val inicio = when (rol) {
        Rol.CONDUCTOR -> Pantalla.InicioConductor
        Rol.ADMIN_ESTACIONAMIENTO -> Pantalla.InicioAdminEstacionamiento
        Rol.ADMIN -> Pantalla.InicioAdmin
    }
    NavHost(navController = nav, startDestination = inicio.ruta) {
        composable(Pantalla.InicioConductor.ruta) {
            PantallaInicio("Conductor", email, onSalir)
        }
        composable(Pantalla.InicioAdminEstacionamiento.ruta) {
            PantallaInicio("Administrador de estacionamiento", email, onSalir)
        }
        composable(Pantalla.InicioAdmin.ruta) {
            PantallaInicio("Administrador", email, onSalir)
        }
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
