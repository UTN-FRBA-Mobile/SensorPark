package com.sensorpark.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themePreferences by preferencesDataStore(name = "theme_preferences")

private val THEME_MODE = stringPreferencesKey("themeMode")

/**
 * Guarda la preferencia de apariencia (sistema, claro u oscuro) en el dispositivo. Por defecto
 * devuelve null, lo que indica usar el modo claro u oscuro configurado en el telefono.
 */
@Singleton
class ThemePreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val themeMode: Flow<String?> = context.themePreferences.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[THEME_MODE] }

    suspend fun setThemeMode(mode: String) {
        context.themePreferences.edit { it[THEME_MODE] = mode }
    }
}
