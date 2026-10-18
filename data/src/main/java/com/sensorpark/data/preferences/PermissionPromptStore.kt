package com.sensorpark.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.permissionPrompts by preferencesDataStore(name = "permission_prompts")

private val ASKED_AFTER_FIRST_RESERVATION = booleanPreferencesKey("askedAfterFirstReservation")

/**
 * Recuerda si ya se le pidieron al usuario los permisos de avisos y de ubicación. Se piden una
 * sola vez y en contexto: después de su primera reserva, que es cuando se entiende para qué son.
 * Si los niega, no se vuelve a insistir.
 */
@Singleton
class PermissionPromptStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val askedAfterFirstReservation: Flow<Boolean> = context.permissionPrompts.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[ASKED_AFTER_FIRST_RESERVATION] == true }

    suspend fun markAskedAfterFirstReservation() {
        context.permissionPrompts.edit { it[ASKED_AFTER_FIRST_RESERVATION] = true }
    }
}
