package com.sensorpark.app.navigation

import com.sensorpark.domain.navigation.DeepLinks

/** Una ruta por pantalla. `pattern` es lo que registra el NavHost; `create` arma la ruta con sus datos. */
sealed class Route(val pattern: String) {
    data object Login : Route("login")
    data object SignUp : Route("sign-up")

    data object Home : Route("home")
    data object Map : Route("map")

    data object ParkingLotDetail : Route("parking-lot/{$ARG_PARKING_LOT_ID}") {
        fun create(parkingLotId: String) = "parking-lot/$parkingLotId"
    }

    data object Reserve : Route("reserve/{$ARG_PARKING_LOT_ID}") {
        fun create(parkingLotId: String) = "reserve/$parkingLotId"
    }

    /** Destino del deep link `sensorpark://reservation/{id}`; sin id muestra la lista completa. */
    data object MyReservations :
        Route("my-reservations?${DeepLinks.ARG_RESERVATION_ID}={${DeepLinks.ARG_RESERVATION_ID}}") {
        fun create() = "my-reservations"
    }

    data object CheckIn : Route("check-in/{${DeepLinks.ARG_RESERVATION_ID}}") {
        fun create(reservationId: String) = "check-in/$reservationId"
    }

    data object Settings : Route("settings")
    data object Admin : Route("admin")
    data object Diagnostics : Route("diagnostics")

    companion object {
        const val ARG_PARKING_LOT_ID = "parkingLotId"
    }
}
