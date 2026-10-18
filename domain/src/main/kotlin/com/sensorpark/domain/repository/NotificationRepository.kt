package com.sensorpark.domain.repository

import com.sensorpark.domain.model.AppNotification

/**
 * Muestra los avisos del teléfono. La implementación real publica una notificación de Android;
 * respeta las preferencias del usuario y no se rompe si falta el permiso.
 */
interface NotificationRepository {
    suspend fun notify(notification: AppNotification)

    /** Saca un aviso ya mostrado, por su `key` (por ejemplo el persistente de la estadía). */
    suspend fun cancel(key: String)
}
