package com.sensorpark.domain.navigation

/** Deep links de la app. Los usan la navegación (app) y las notificaciones (data). */
object DeepLinks {
    const val ARG_RESERVATION_ID = "reservationId"
    const val RESERVATION = "sensorpark://reservation/{$ARG_RESERVATION_ID}"

    fun reservation(id: String) = "sensorpark://reservation/$id"
}
