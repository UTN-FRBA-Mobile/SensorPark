package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.NotificationRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * El aviso de "se liberó un lugar" (T18) mientras la app está abierta. En producción lo manda el
 * backend por push; este es el respaldo para cuando la push no llega (el emulador de Firebase no
 * manda pushes, o el token todavía no se guardó). Los dos usan la misma clave: se ve uno solo.
 *
 * Como [SyncReservationAlertsUseCase], reconcilia: avisa de cada oferta vigente una sola vez y
 * saca el aviso cuando la oferta deja de estar (la aceptó, la rechazó o se le pasó). Recuerda en
 * memoria qué ofertas ya avisó, así una vez descartado el aviso no vuelve a aparecer.
 */
class SyncWaitlistAlertsUseCase @Inject constructor(
    private val notifications: NotificationRepository,
    private val messages: ReservationAlertMessages,
    private val clock: Clock,
) {
    private val announced = mutableSetOf<String>()

    suspend fun sync(entries: List<WaitlistEntry>, lots: Map<String, ParkingLot>) {
        val now = clock.instant()
        entries.forEach { entry ->
            val offer = entry.offer
            if (entry.status == WaitlistStatus.OFFERED && offer != null && offer.expiresAt > now) {
                // La misma entrada puede recibir otra oferta más tarde: cuenta cada una.
                if (announced.add(offerId(entry.id, offer.offeredAt))) {
                    notifications.notify(messages.waitlistOffer(entry, lots[entry.parkingLotId]?.name.orEmpty()))
                }
            } else if (entry.status != WaitlistStatus.WAITING) {
                notifications.cancel(messages.waitlistOfferKey(entry.id))
            }
        }
    }

    private fun offerId(entryId: String, offeredAt: Instant) = "$entryId@$offeredAt"
}
