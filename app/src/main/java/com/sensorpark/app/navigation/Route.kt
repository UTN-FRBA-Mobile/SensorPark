package com.sensorpark.app.navigation

import android.net.Uri
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.navigation.DeepLinks

/** Una ruta por pantalla. `pattern` es lo que registra el NavHost; `create` arma la ruta con sus datos. */
sealed class Route(val pattern: String) {
    data object Login : Route("login")
    data object SignUp : Route("sign-up")

    // Conductor
    data object Map : Route("map")

    data object ParkingLotDetail : Route("parking-lot/{$ARG_PARKING_LOT_ID}") {
        fun create(parkingLotId: String) = "parking-lot/$parkingLotId"
    }

    /** Con un lugar del plano (T12) la ruta suma su id, su código y su tipo. */
    data object Reserve :
        Route("reserve/{$ARG_PARKING_LOT_ID}?$ARG_SPOT_ID={$ARG_SPOT_ID}&$ARG_SPOT_CODE={$ARG_SPOT_CODE}&$ARG_SPOT_TYPE={$ARG_SPOT_TYPE}") {
        fun create(parkingLotId: String, spot: Spot? = null): String {
            val base = "reserve/$parkingLotId"
            if (spot == null) return base
            return "$base?$ARG_SPOT_ID=${Uri.encode(spot.id)}&$ARG_SPOT_CODE=${Uri.encode(spot.code)}&$ARG_SPOT_TYPE=${spot.type.name}"
        }
    }

    /** Destino del deep link `sensorpark://reservation/{id}`; sin id muestra la lista completa. */
    data object MyReservations :
        Route("my-reservations?${DeepLinks.ARG_RESERVATION_ID}={${DeepLinks.ARG_RESERVATION_ID}}")

    data object CheckIn : Route("check-in/{${DeepLinks.ARG_RESERVATION_ID}}") {
        fun create(reservationId: String) = "check-in/$reservationId"
    }

    /** La oferta de un lugar liberado de la lista de espera; destino de `sensorpark://waitlist-offer/{id}`. */
    data object WaitlistOffer : Route("waitlist-offer/{${DeepLinks.ARG_ENTRY_ID}}") {
        fun create(entryId: String) = "waitlist-offer/$entryId"
    }

    // Administrador de estacionamiento
    data object ManagedParkingLots : Route("parking-lots")

    data object ParkingLotPanel : Route("parking-lot-panel/{$ARG_PARKING_LOT_ID}") {
        fun create(parkingLotId: String) = "parking-lot-panel/$parkingLotId"
    }

    data object CreateParkingLot : Route("create-parking-lot")

    // Todos
    data object Account : Route("account")
    data object Diagnostics : Route("diagnostics")

    companion object {
        const val ARG_PARKING_LOT_ID = "parkingLotId"
        const val ARG_SPOT_ID = "spotId"
        const val ARG_SPOT_CODE = "spotCode"
        const val ARG_SPOT_TYPE = "spotType"
    }
}
