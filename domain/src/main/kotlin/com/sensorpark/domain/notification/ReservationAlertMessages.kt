package com.sensorpark.domain.notification

import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.AppNotification
import java.time.Instant

/**
 * Los textos de los avisos que arma el propio teléfono. El dominio decide cuándo avisar; la
 * implementación, en `data`, los saca de los recursos y los traduce.
 */
interface ReservationAlertMessages {
    fun reservationConfirmed(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ): AppNotification

    fun arrivalReminder(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ): AppNotification

    /** El aviso de llegada, con el botón que abre el check-in. */
    fun arrival(geofence: ActiveGeofence): AppNotification

    /** El aviso persistente mientras el auto está estacionado, con el botón "Salir". */
    fun stay(reservationId: String, spotCode: String, endsAt: Instant): AppNotification

    /** La clave del aviso persistente, para poder sacarlo al hacer el check-out. */
    fun stayKey(reservationId: String): String

    /**
     * Las claves de los avisos que sirven sólo antes de llegar (confirmación, recordatorio y
     * llegada), para sacarlos todos una vez hecho el check-in: ya no tienen sentido.
     */
    fun preArrivalKeys(reservationId: String): List<String>
}
