package com.sensorpark.domain.repository

import com.sensorpark.domain.model.NotificationPreferences
import com.sensorpark.domain.model.NotificationType
import kotlinx.coroutines.flow.Flow

/**
 * Qué tipos de aviso quiere recibir el usuario. Se guardan en el teléfono (DataStore) y también en
 * su documento de Firestore, para que el backend no mande pushes que el usuario apagó.
 */
interface NotificationPreferencesRepository {
    val preferences: Flow<NotificationPreferences>

    suspend fun setEnabled(type: NotificationType, enabled: Boolean)
}
