package com.sensorpark.domain.repository

import java.time.Instant

/**
 * Programa el recordatorio previo a la hora de llegada. La implementación usa WorkManager, que
 * sobrevive al cierre de la app y al reinicio del teléfono.
 */
interface ReminderScheduler {
    /**
     * Programa el aviso de [reservationId] para [notifyAt]. Si ya había uno para esa reserva lo
     * reemplaza, y si la hora ya pasó no programa nada.
     */
    suspend fun scheduleArrivalReminder(reservationId: String, notifyAt: Instant)

    suspend fun cancel(reservationId: String)
}
