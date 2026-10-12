package com.sensorpark.domain.navegacion

/** Deep links de la app. Los usan la navegación (app) y las notificaciones (data). */
object Enlaces {
    const val ARG_RESERVA_ID = "reservaId"
    const val RESERVA = "sensorpark://reserva/{$ARG_RESERVA_ID}"

    fun reserva(id: String) = "sensorpark://reserva/$id"
}
