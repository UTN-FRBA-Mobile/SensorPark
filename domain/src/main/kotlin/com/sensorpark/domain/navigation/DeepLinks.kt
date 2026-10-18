package com.sensorpark.domain.navigation

import com.sensorpark.domain.model.NotificationType

/** Deep links de la app. Los usan la navegación (app) y las notificaciones (data). */
object DeepLinks {
    const val ARG_RESERVATION_ID = "reservationId"
    const val RESERVATION = "sensorpark://reservation/{$ARG_RESERVATION_ID}"
    const val CHECK_IN = "sensorpark://check-in/{$ARG_RESERVATION_ID}"

    fun reservation(id: String) = "sensorpark://reservation/$id"

    fun checkIn(id: String) = "sensorpark://check-in/$id"

    /**
     * A qué pantalla lleva cada tipo de aviso: el de llegada abre directamente el check-in (T21),
     * el resto abre la reserva en "Mis reservas". Sin id no hay a dónde ir y abre el inicio.
     */
    fun forNotification(type: NotificationType, id: String): String? = when {
        id.isEmpty() -> null
        type == NotificationType.ARRIVAL -> checkIn(id)
        else -> reservation(id)
    }
}
