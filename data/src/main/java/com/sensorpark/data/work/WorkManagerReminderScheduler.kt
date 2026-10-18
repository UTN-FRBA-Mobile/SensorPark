package com.sensorpark.data.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sensorpark.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programa el recordatorio con WorkManager, que sobrevive al cierre de la app y al reinicio del
 * teléfono. Un nombre único por reserva hace que reprogramar reemplace y que cancelar alcance.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {

    override suspend fun scheduleArrivalReminder(reservationId: String, notifyAt: Instant) {
        val delay = Duration.between(clock.instant(), notifyAt)
        // Una reserva para dentro de un rato ya pasó la hora del recordatorio: no se avisa.
        if (delay.isNegative || delay.isZero) return

        val request = OneTimeWorkRequestBuilder<ArrivalReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ArrivalReminderWorker.KEY_RESERVATION_ID to reservationId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(reservationId), ExistingWorkPolicy.REPLACE, request)
    }

    override suspend fun cancel(reservationId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reservationId))
    }

    private fun workName(reservationId: String) = "arrival-reminder-$reservationId"
}
