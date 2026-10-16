package com.sensorpark.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.sensorpark.app.R
import com.sensorpark.domain.model.Role

/**
 * Pestañas de la barra inferior. Cada una es un grafo de navegación propio ([graph]), así cada
 * pestaña conserva su pila de pantallas al ir y volver entre ellas.
 */
enum class Tab(val graph: String, val start: Route, @StringRes val label: Int, val icon: ImageVector) {
    SEARCH("tab-search", Route.Map, R.string.tab_search, Icons.Filled.Search),
    RESERVATIONS("tab-reservations", Route.MyReservations, R.string.tab_reservations, Icons.Filled.DateRange),
    PARKING_LOTS("tab-parking-lots", Route.ManagedParkingLots, R.string.tab_parking_lots, Icons.Filled.Place),
    ACCOUNT("tab-account", Route.Account, R.string.tab_account, Icons.Filled.Person);

    companion object {
        /** El conductor busca y reserva; el administrador (y ADMIN, que ve todos) administra estacionamientos. */
        fun forRole(role: Role): List<Tab> = when (role) {
            Role.DRIVER -> listOf(SEARCH, RESERVATIONS, ACCOUNT)
            Role.PARKING_ADMIN, Role.ADMIN -> listOf(PARKING_LOTS, ACCOUNT)
        }
    }
}
