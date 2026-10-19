package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.model.WaitlistOffer
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.NotificationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SyncWaitlistAlertsUseCaseTest {
    private val now = Instant.parse("2026-10-18T15:00:00Z")
    private val lot = ParkingLot("utn", "UTN Medrano", "", Coordinates(-34.5984, -58.42))
    private val notifications = RecordingNotifications()
    private val sync = SyncWaitlistAlertsUseCase(notifications, Messages, Clock.fixed(now, ZoneOffset.UTC))

    private fun entry(status: WaitlistStatus, offeredAt: Instant? = null) = WaitlistEntry(
        "w1", "ana", "utn", SpotType.STANDARD, now.plus(Duration.ofHours(2)), 60, Category.STANDARD,
        now.minus(Duration.ofMinutes(30)), status,
        offer = offeredAt?.let { WaitlistOffer("01", "01", it, it.plus(Duration.ofMinutes(5))) },
    )

    @Test
    fun `una oferta nueva avisa una sola vez`() = runTest {
        val offered = entry(WaitlistStatus.OFFERED, offeredAt = now)
        sync.sync(listOf(offered), mapOf("utn" to lot))
        sync.sync(listOf(offered), mapOf("utn" to lot))

        assertEquals(listOf("waitlist_offerw1"), notifications.shown.map { it.key })
        assertEquals("UTN Medrano", notifications.shown.single().body)
    }

    @Test
    fun `si la entrada recibe otra oferta mas tarde, avisa de nuevo`() = runTest {
        sync.sync(listOf(entry(WaitlistStatus.OFFERED, offeredAt = now.minus(Duration.ofMinutes(4)))), emptyMap())
        sync.sync(listOf(entry(WaitlistStatus.OFFERED, offeredAt = now)), emptyMap())
        assertEquals(2, notifications.shown.size)
    }

    @Test
    fun `una oferta vencida no avisa y saca el aviso`() = runTest {
        sync.sync(listOf(entry(WaitlistStatus.OFFERED, offeredAt = now.minus(Duration.ofMinutes(6)))), emptyMap())
        assertTrue(notifications.shown.isEmpty())
        assertEquals(listOf("waitlist_offerw1"), notifications.cancelled)
    }

    @Test
    fun `al aceptar o rechazar se saca el aviso, y esperando no se toca`() = runTest {
        sync.sync(listOf(entry(WaitlistStatus.WAITING)), emptyMap())
        assertTrue(notifications.cancelled.isEmpty())

        sync.sync(listOf(entry(WaitlistStatus.ACCEPTED)), emptyMap())
        sync.sync(listOf(entry(WaitlistStatus.DECLINED)), emptyMap())
        assertEquals(listOf("waitlist_offerw1", "waitlist_offerw1"), notifications.cancelled)
    }
}

private class RecordingNotifications : NotificationRepository {
    val shown = mutableListOf<AppNotification>()
    val cancelled = mutableListOf<String>()

    override suspend fun notify(notification: AppNotification) {
        shown += notification
    }

    override suspend fun cancel(key: String) {
        cancelled += key
    }
}

private object Messages : ReservationAlertMessages {
    override fun reservationConfirmed(reservationId: String, parkingLotName: String, spotCode: String, arrivesAt: Instant) =
        throw UnsupportedOperationException()

    override fun arrivalReminder(reservationId: String, parkingLotName: String, spotCode: String, arrivesAt: Instant) =
        throw UnsupportedOperationException()

    override fun arrival(geofence: com.sensorpark.domain.model.ActiveGeofence) = throw UnsupportedOperationException()

    override fun stay(reservationId: String, spotCode: String, endsAt: Instant) = throw UnsupportedOperationException()

    override fun stayKey(reservationId: String) = "stay$reservationId"

    override fun preArrivalKeys(reservationId: String) = emptyList<String>()

    override fun waitlistOffer(entry: WaitlistEntry, parkingLotName: String) =
        AppNotification(NotificationType.WAITLIST, waitlistOfferKey(entry.id), "Se liberó un lugar", parkingLotName)

    override fun waitlistOfferKey(entryId: String) = "waitlist_offer$entryId"
}
