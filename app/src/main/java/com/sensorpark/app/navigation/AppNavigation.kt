package com.sensorpark.app.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.sensorpark.app.ui.alerts.AlertsHost
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
import com.sensorpark.feature.reservations.WaitlistOfferScreen

/**
 * Raíz de la UI: según la sesión muestra el ingreso o la app. [pendingDeepLink] es un deep link que
 * llegó con la app ya creada (por ejemplo, al tocar un aviso); se abre cuando hay sesión.
 */
@Composable
fun AppNavigation(
    pendingDeepLink: Intent? = null,
    onDeepLinkHandled: () -> Unit = {},
    vm: SessionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
                    MainNavigation(current, role, pendingDeepLink, onDeepLinkHandled, onSignOut = vm::signOut)
                }
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
private fun MainNavigation(
    state: SessionState.SignedIn,
    role: Role,
    pendingDeepLink: Intent?,
    onDeepLinkHandled: () -> Unit,
    onSignOut: () -> Unit,
) {
    val nav = rememberNavController()
    // Después de la composición, cuando el NavHost ya tiene su grafo.
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            nav.handleDeepLink(pendingDeepLink)
            onDeepLinkHandled()
        }
    }
    val tabs = Tab.forRole(role)
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    // Avisos, recordatorios, geocercas y permisos: no dibuja nada, cuelga de la pantalla principal.
    if (role == Role.DRIVER) AlertsHost(snackbarHostState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val borderColor = MaterialTheme.colorScheme.outlineVariant
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokeWidth,
                    )
                },
            ) {
                tabs.forEach { tab ->
                    val selected = destination?.hierarchy?.any { it.route == tab.graph } == true
                    NavigationBarItem(
                        selected = selected,
                        // Volver a tocar la pestaña en la que se está lleva a su inicio, como en
                        // cualquier app: si no, desde el check-in no había forma de llegar a la lista.
                        onClick = { if (selected) nav.goToTabStart(tab) else nav.navigateToTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(tab.label),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        ),
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

/** Abre una pestaña en su inicio, olvidando la pila que había quedado guardada. */
private fun NavHostController.openTabAtStart(tab: Tab) {
    clearBackStack(tab.graph)
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/**
 * Lleva al inicio de la pestaña actual. Si el inicio no está en la pila (se llegó por un deep link
 * directo a una pantalla interna), lo abre.
 */
private fun NavHostController.goToTabStart(tab: Tab) {
    if (!popBackStack(tab.start.pattern, inclusive = false)) {
        navigate(tab.start.pattern.substringBefore("?")) { launchSingleTop = true }
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
                onReserveSpot = { lotId, spotId -> nav.navigate(Route.Reserve.create(lotId, spotId)) },
            )
        }
        composable(
            route = Route.Reserve.pattern,
            arguments = listOf(
                navArgument(Route.ARG_SPOT_ID) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { entry ->
            ReserveScreen(
                parkingLotId = entry.arguments?.getString(Route.ARG_PARKING_LOT_ID).orEmpty(),
                spotId = entry.arguments?.getString(Route.ARG_SPOT_ID),
                // A la lista, donde está la reserva nueva: no a la pantalla que había quedado
                // abierta en esa pestaña (por ejemplo el resumen de una reserva anterior).
                onOpenMyReservations = { nav.openTabAtStart(Tab.RESERVATIONS) },
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
                onOpenOffer = { nav.navigate(Route.WaitlistOffer.create(it)) },
            )
        }
        // El aviso de "se liberó un lugar" abre acá, con sensorpark://waitlist-offer/{id} (T18).
        composable(
            route = Route.WaitlistOffer.pattern,
            deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.WAITLIST_OFFER }),
        ) { entry ->
            WaitlistOfferScreen(
                entryId = entry.arguments?.getString(DeepLinks.ARG_ENTRY_ID).orEmpty(),
                onOpenMyReservations = { nav.openTabAtStart(Tab.RESERVATIONS) },
            )
        }
        // El aviso de llegada abre acá directamente, con sensorpark://check-in/{id}.
        composable(
            route = Route.CheckIn.pattern,
            deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.CHECK_IN }),
        ) { entry ->
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
            CreateParkingLotScreen(
                // Al panel del nuevo, para cargarle los lugares; "atrás" vuelve a la lista, no al formulario.
                onCreated = { id ->
                    nav.navigate(Route.ParkingLotPanel.create(id)) {
                        popUpTo(Route.CreateParkingLot.pattern) { inclusive = true }
                    }
                },
            )
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

/**
 * Registra el token de push al arrancar con sesión (no alcanza con onNewToken). El permiso de
 * notificaciones ya no se pide acá: lo pide [AlertsHost] después de la primera reserva, que es
 * cuando se entiende para qué sirve.
 */
@Composable
private fun RegisterDevice(register: () -> Unit) {
    LaunchedEffect(Unit) { register() }
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
