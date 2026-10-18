package com.sensorpark.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.NotificationRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * El recordatorio previo a la hora de llegada. Antes de avisar vuelve a mirar la reserva: si se
 * canceló, venció o ya hizo check-in, no muestra nada (el trabajo también se cancela al cancelar
 * la reserva, pero puede haber quedado encolado).
 *
 * Toma sus dependencias con un EntryPoint en vez de @HiltWorker para no sumar `hilt-work` ni una
 * WorkerFactory propia en la Application.
 */
class ArrivalReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reservations(): ReservationRepository

        fun parkingLots(): ParkingLotRepository

        fun notifications(): NotificationRepository

        fun messages(): ReservationAlertMessages
    }

    override suspend fun doWork(): Result {
        val reservationId = inputData.getString(KEY_RESERVATION_ID) ?: return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return try {
            val mine = (deps.reservations().myReservations().first() as? DataResult.Success)?.value.orEmpty()
            val reservation = mine.firstOrNull { it.id == reservationId }
                ?: return Result.success()
            if (reservation.status != ReservationStatus.CONFIRMED) return Result.success()

            val lots = (deps.parkingLots().parkingLots().first() as? DataResult.Success)?.value.orEmpty()
            val lot = lots.firstOrNull { it.id == reservation.parkingLotId } ?: return Result.success()

            deps.notifications().notify(
                deps.messages().arrivalReminder(
                    reservationId = reservation.id,
                    parkingLotName = lot.name,
                    spotCode = reservation.spotCode,
                    arrivesAt = reservation.arrivesAt,
                ),
            )
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mostrar el recordatorio de $reservationId", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_RESERVATION_ID = "reservationId"
        private const val TAG = "SensorParkReminder"
    }
}
