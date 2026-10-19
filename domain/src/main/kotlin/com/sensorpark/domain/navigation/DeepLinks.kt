package com.sensorpark.domain.navigation

import com.sensorpark.domain.model.NotificationType

/** Deep links de la app. Los usan la navegación (app) y las notificaciones (data). */
object DeepLinks {
    const val ARG_RESERVATION_ID = "reservationId"
    const val RESERVATION = "sensorpark://reservation/{$ARG_RESERVATION_ID}"
    const val CHECK_IN = "sensorpark://check-in/{$ARG_RESERVATION_ID}"
    const val ARG_ENTRY_ID = "entryId"
    const val WAITLIST_OFFER = "sensorpark://waitlist-offer/{$ARG_ENTRY_ID}"

    fun reservation(id: String) = "sensorpark://reservation/$id"

    fun checkIn(id: String) = "sensorpark://check-in/$id"

    /** La oferta de un lugar liberado, para aceptarla o rechazarla. */
    fun waitlistOffer(entryId: String) = "sensorpark://waitlist-offer/$entryId"

    /**
     * A qué pantalla lleva cada tipo de aviso: el de llegada abre directamente el check-in (T21),
     * el de la lista de espera abre la oferta (T18) y el resto abre la reserva en "Mis reservas".
     * Sin id no hay a dónde ir y abre el inicio.
     */
    fun forNotification(type: NotificationType, id: String): String? = when {
        id.isEmpty() -> null
        type == NotificationType.ARRIVAL -> checkIn(id)
        type == NotificationType.WAITLIST -> waitlistOffer(id)
        else -> reservation(id)
    }
}
