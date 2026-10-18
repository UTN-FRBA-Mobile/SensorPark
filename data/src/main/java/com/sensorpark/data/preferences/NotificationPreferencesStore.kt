package com.sensorpark.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sensorpark.data.firebase.Collections
import com.sensorpark.data.firebase.UserFields
import com.sensorpark.domain.model.NotificationPreferences
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.repository.NotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationPreferences by preferencesDataStore(name = "notification_preferences")

/** Sólo se guardan los tipos apagados: lo que no está en la lista está encendido. */
private val DISABLED_TYPES = stringSetPreferencesKey("disabledTypes")

/**
 * Las preferencias viven en el teléfono (DataStore): así las leen también los receivers, sin red
 * y sin sesión. Además se copian al documento del usuario en Firestore, para que el backend (T17)
 * no mande pushes que el usuario apagó. Esa copia es de buena fe: si falla, la app sigue igual.
 */
@Singleton
class NotificationPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : NotificationPreferencesRepository {

    override val preferences: Flow<NotificationPreferences> = context.notificationPreferences.data
        // Un archivo ilegible no puede dejar a la app sin avisos: se arranca con todo encendido.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { stored ->
            NotificationPreferences(
                stored[DISABLED_TYPES].orEmpty().mapNotNull { NotificationType.from(it) }.toSet(),
            )
        }

    override suspend fun setEnabled(type: NotificationType, enabled: Boolean) {
        val updated = context.notificationPreferences.edit { stored ->
            val disabled = stored[DISABLED_TYPES].orEmpty()
            stored[DISABLED_TYPES] = if (enabled) disabled - type.name else disabled + type.name
        }
        mirrorToFirestore(updated[DISABLED_TYPES].orEmpty())
    }

    private suspend fun mirrorToFirestore(disabled: Set<String>) {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection(Collections.USERS).document(uid)
                .set(mapOf(UserFields.NOTIFICATIONS_DISABLED to disabled.toList()), SetOptions.merge())
                .await()
        }.onFailure { Log.w(TAG, "No se pudieron copiar las preferencias de avisos a Firestore", it) }
    }

    private companion object {
        const val TAG = "SensorParkPrefs"
    }
}
