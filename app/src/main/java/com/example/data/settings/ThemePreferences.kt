package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

class ThemePreferences(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val savedName = preferences[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        try {
            AppThemeMode.valueOf(savedName)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }
}
