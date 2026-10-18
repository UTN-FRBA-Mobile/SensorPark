package com.sensorpark.data.notifications

import android.content.Context
import com.sensorpark.data.R
import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.NotificationAction
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.navigation.DeepLinks
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.reservation.ReservationRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Los textos de los avisos locales, en español y en la hora argentina. */
@Singleton
class ResourceReservationAlertMessages @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReservationAlertMessages {

    override fun reservationConfirmed(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: java.time.Instant,
    ) = AppNotification(
        type = NotificationType.RESERVATION,
        key = confirmedKey(reservationId),
        title = context.getString(R.string.notification_reservation_confirmed_title),
        body = context.getString(
            R.string.notification_reservation_confirmed_body,
            parkingLotName,
            spotCode,
            moment(arrivesAt),
        ),
        deepLink = DeepLinks.reservation(reservationId),
    )

    override fun arrivalReminder(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: java.time.Instant,
    ) = AppNotification(
        type = NotificationType.REMINDER,
        key = reminderKey(reservationId),
        title = context.getString(R.string.notification_reminder_title),
        body = context.getString(R.string.notification_reminder_body, parkingLotName, spotCode, moment(arrivesAt)),
        deepLink = DeepLinks.reservation(reservationId),
    )

    override fun arrival(geofence: ActiveGeofence) = AppNotification(
        type = NotificationType.ARRIVAL,
        key = arrivalKey(geofence.reservationId),
        title = context.getString(R.string.notification_arrival_title, geofence.parkingLotName),
        body = context.getString(R.string.notification_arrival_body, geofence.spotCode),
        deepLink = DeepLinks.checkIn(geofence.reservationId),
        actions = listOf(
            NotificationAction(
                label = context.getString(R.string.notification_arrival_action),
                deepLink = DeepLinks.checkIn(geofence.reservationId),
            ),
        ),
    )

    override fun stay(reservationId: String, spotCode: String, endsAt: java.time.Instant) = AppNotification(
        type = NotificationType.RESERVATION,
        key = stayKey(reservationId),
        title = context.getString(R.string.notification_stay_title, spotCode),
        body = context.getString(R.string.notification_stay_body, TIME.format(endsAt)),
        // "Salir" abre la misma pantalla de check-in, que con la reserva en uso ofrece el check-out.
        deepLink = DeepLinks.checkIn(reservationId),
        actions = listOf(
            NotificationAction(
                label = context.getString(R.string.notification_stay_action),
                deepLink = DeepLinks.checkIn(reservationId),
            ),
        ),
        ongoing = true,
    )

    override fun stayKey(reservationId: String) = "stay$reservationId"

    override fun preArrivalKeys(reservationId: String) =
        listOf(confirmedKey(reservationId), reminderKey(reservationId), arrivalKey(reservationId))

    private fun confirmedKey(reservationId: String) = "reservationConfirmed$reservationId"

    private fun reminderKey(reservationId: String) = "reminder$reservationId"

    private fun arrivalKey(reservationId: String) = "arrival$reservationId"

    /** "16:30" si es hoy; si no, "18/10 16:30". */
    private fun moment(instant: java.time.Instant): String {
        val today = clock.instant().atZone(ReservationRules.ZONE).toLocalDate()
        val day = instant.atZone(ReservationRules.ZONE).toLocalDate()
        return if (day == today) TIME.format(instant) else DAY_AND_TIME.format(instant)
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ReservationRules.ZONE)
        val DAY_AND_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ReservationRules.ZONE)
    }
}
