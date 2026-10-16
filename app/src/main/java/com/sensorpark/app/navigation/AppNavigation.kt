package com.sensorpark.app.navigation

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sensorpark.app.R
import com.sensorpark.app.ui.account.AccountScreen
import com.sensorpark.app.ui.diagnostics.DiagnosticsScreen
import com.sensorpark.app.ui.login.LoginScreen
import com.sensorpark.app.ui.login.SignUpScreen
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.SessionState
import com.sensorpark.domain.navigation.DeepLinks
import com.sensorpark.feature.admin.CreateParkingLotScreen
import com.sensorpark.feature.admin.ManagedParkingLotsScreen
import com.sensorpark.feature.admin.ParkingLotPanelScreen
import com.sensorpark.feature.checkin.CheckInScreen
import com.sensorpark.feature.map.MapScreen
import com.sensorpark.feature.parking.ParkingLotDetailScreen
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
            val role = current.role
            if (role == null) {
                Loading(message = stringResource(R.string.session_preparing), onSignOut = vm::signOut)
            } else {
                // Recién con el rol asignado: si el token se guardara antes, podría crear el documento
                // del usuario antes que el registro, y el rol pedido se perdería.
                RegisterDevice(vm::registerDevice)
                MainNavigation(current, role, onSignOut = vm::signOut)
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
 * La app con sesión: una barra inferior con las pestañas del rol (ver [Tab]). Los features no se
 * conocen entre sí ni conocen las rutas: cada pantalla recibe lambdas y acá se decide a dónde lleva.
 */
@Composable
private fun MainNavigation(state: SessionState.SignedIn, role: Role, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    HandleNewDeepLinks(nav)
    val tabs = Tab.forRole(role)
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.graph } == true,
                        onClick = { nav.navigateToTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = tabs.first().graph,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            if (role == Role.DRIVER) {
                searchTab(nav)
                reservationsTab(nav)
            } else {
                parkingLotsTab(nav, uid = state.session.uid, seesAll = role == Role.ADMIN)
            }
            accountTab(nav, state, onSignOut)
        }
    }
}

/** Cambia de pestaña conservando la pila de cada una, como recomienda Navigation. */
private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraphBuilder.searchTab(nav: NavHostController) {
    navigation(startDestination = Tab.SEARCH.start.pattern, route = Tab.SEARCH.graph) {
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
                onOpenMyReservations = { nav.navigateToTab(Tab.RESERVATIONS) },
            )
        }
    }
}

private fun NavGraphBuilder.reservationsTab(nav: NavHostController) {
    navigation(startDestination = Tab.RESERVATIONS.start.pattern, route = Tab.RESERVATIONS.graph) {
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
    }
}

private fun NavGraphBuilder.parkingLotsTab(nav: NavHostController, uid: String, seesAll: Boolean) {
    navigation(startDestination = Tab.PARKING_LOTS.start.pattern, route = Tab.PARKING_LOTS.graph) {
        composable(Route.ManagedParkingLots.pattern) {
            ManagedParkingLotsScreen(
                uid = uid,
                seesAll = seesAll,
                onOpenParkingLot = { nav.navigate(Route.ParkingLotPanel.create(it)) },
                onCreateParkingLot = { nav.navigate(Route.CreateParkingLot.pattern) },
            )
        }
        composable(Route.ParkingLotPanel.pattern) { entry ->
            ParkingLotPanelScreen(parkingLotId = entry.arguments?.getString(Route.ARG_PARKING_LOT_ID).orEmpty())
        }
        composable(Route.CreateParkingLot.pattern) {
            CreateParkingLotScreen()
        }
    }
}

private fun NavGraphBuilder.accountTab(nav: NavHostController, state: SessionState.SignedIn, onSignOut: () -> Unit) {
    navigation(startDestination = Tab.ACCOUNT.start.pattern, route = Tab.ACCOUNT.graph) {
        composable(Route.Account.pattern) {
            AccountScreen(
                state = state,
                onSignOut = onSignOut,
                onOpenDiagnostics = { nav.navigate(Route.Diagnostics.pattern) },
            )
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
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (message != null) Text(message)
        if (onSignOut != null) TextButton(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) }
    }
}
