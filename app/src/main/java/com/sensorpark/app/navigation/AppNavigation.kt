package com.sensorpark.app.navigation

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
import com.sensorpark.app.ui.diagnostics.DiagnosticsScreen
import com.sensorpark.app.ui.home.HomeScreen
import com.sensorpark.app.ui.login.LoginScreen
import com.sensorpark.app.ui.login.SignUpScreen
import com.sensorpark.app.ui.settings.SettingsScreen
import com.sensorpark.domain.model.SessionState
import com.sensorpark.domain.navigation.DeepLinks
import com.sensorpark.feature.admin.AdminScreen
import com.sensorpark.feature.checkin.CheckInScreen
import com.sensorpark.feature.parking.ParkingLotDetailScreen
import com.sensorpark.feature.map.MapScreen
import com.sensorpark.feature.reservations.MyReservationsScreen
import com.sensorpark.feature.reservations.ReserveScreen

/** Raíz de la UI: según la sesión muestra el ingreso o la app. */
@Composable
fun AppNavigation(vm: SessionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val current = state) {
        null -> Loading()
        SessionState.SignedOut -> AuthNavigation()
        is SessionState.SignedIn -> {
            if (current.role == null) {
                Loading(message = "Preparando tu cuenta…", onSignOut = vm::signOut)
            } else {
                // Recién con el rol asignado: si el token se guardara antes, podría crear el documento
                // del usuario antes que el registro, y el rol pedido se perdería.
                RegisterDevice(vm::registerDevice)
                MainNavigation(current, onSignOut = vm::signOut)
            }
        }
    }
}

@Composable
private fun AuthNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Login.pattern) {
        composable(Route.Login.pattern) {
            LoginScreen(onSignUp = { nav.navigate(Route.SignUp.pattern) })
        }
        composable(Route.SignUp.pattern) {
            SignUpScreen(onBack = { nav.popBackStack() })
        }
    }
}

/**
 * Grafo de la app con sesión. Los features no se conocen entre sí ni conocen las rutas:
 * cada pantalla recibe lambdas y acá se decide a dónde lleva cada una.
 */
@Composable
private fun MainNavigation(state: SessionState.SignedIn, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    HandleNewDeepLinks(nav)

    NavHost(navController = nav, startDestination = Route.Home.pattern) {
        composable(Route.Home.pattern) {
            HomeScreen(state = state, navigateTo = { nav.navigate(it) })
        }
        composable(Route.Map.pattern) {
            MapScreen(onOpenParkingLot = { nav.navigate(Route.ParkingLotDetail.create(it)) })
        }
        composable(Route.ParkingLotDetail.pattern) { entry ->
            ParkingLotDetailScreen(
                parkingLotId = entry.arguments?.getString(Route.ARG_PARKING_LOT_ID).orEmpty(),
                onReserve = { nav.navigate(Route.Reserve.create(it)) },
            )
        }
        composable(Route.Reserve.pattern) { entry ->
            ReserveScreen(
                parkingLotId = entry.arguments?.getString(Route.ARG_PARKING_LOT_ID).orEmpty(),
                onOpenMyReservations = { nav.navigate(Route.MyReservations.create()) },
            )
        }
        composable(
            route = Route.MyReservations.pattern,
            arguments = listOf(
                navArgument(DeepLinks.ARG_RESERVATION_ID) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.RESERVATION }),
        ) { entry ->
            MyReservationsScreen(
                reservationId = entry.arguments?.getString(DeepLinks.ARG_RESERVATION_ID),
                onCheckIn = { nav.navigate(Route.CheckIn.create(it)) },
            )
        }
        composable(Route.CheckIn.pattern) { entry ->
            CheckInScreen(reservationId = entry.arguments?.getString(DeepLinks.ARG_RESERVATION_ID).orEmpty())
        }
        composable(Route.Settings.pattern) {
            SettingsScreen(role = state.role, onSignOut = onSignOut)
        }
        composable(Route.Admin.pattern) {
            AdminScreen()
        }
        composable(Route.Diagnostics.pattern) {
            DiagnosticsScreen()
        }
    }
}

/** Con la app ya abierta, un deep link llega por onNewIntent y hay que pasárselo al NavController. */
@Composable
private fun HandleNewDeepLinks(nav: NavHostController) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity, nav) {
        val listener = Consumer<Intent> { nav.handleDeepLink(it) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

/** Pide permiso de notificaciones y registra el token al arrancar con sesión (no alcanza con onNewToken). */
@Composable
private fun RegisterDevice(register: () -> Unit) {
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        register()
    }
}

@Composable
private fun Loading(message: String? = null, onSignOut: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (message != null) Text(message)
        if (onSignOut != null) TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
    }
}
