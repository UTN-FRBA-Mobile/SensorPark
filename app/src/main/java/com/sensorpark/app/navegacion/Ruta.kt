package com.sensorpark.app.navegacion

import com.sensorpark.domain.navegacion.Enlaces

/** Una ruta por pantalla. `patron` es lo que registra el NavHost; `crear` arma la ruta con sus datos. */
sealed class Ruta(val patron: String) {
    data object Login : Ruta("login")
    data object Registro : Ruta("registro")

    data object Inicio : Ruta("inicio")
    data object Mapa : Ruta("mapa")

    data object DetalleEstacionamiento : Ruta("estacionamiento/{$ARG_ESTACIONAMIENTO_ID}") {
        fun crear(estacionamientoId: String) = "estacionamiento/$estacionamientoId"
    }

    data object Reservar : Ruta("reservar/{$ARG_ESTACIONAMIENTO_ID}") {
        fun crear(estacionamientoId: String) = "reservar/$estacionamientoId"
    }

    /** Destino del deep link `sensorpark://reserva/{id}`; sin id muestra la lista completa. */
    data object MisReservas : Ruta("mis-reservas?${Enlaces.ARG_RESERVA_ID}={${Enlaces.ARG_RESERVA_ID}}") {
        fun crear() = "mis-reservas"
    }

    data object CheckIn : Ruta("check-in/{${Enlaces.ARG_RESERVA_ID}}") {
        fun crear(reservaId: String) = "check-in/$reservaId"
    }

    data object Preferencias : Ruta("preferencias")
    data object Admin : Ruta("admin")
    data object Diagnostico : Ruta("diagnostico")

    companion object {
        const val ARG_ESTACIONAMIENTO_ID = "estacionamientoId"
    }
}
