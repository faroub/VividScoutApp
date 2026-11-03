package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class WalkingNavigationMode {
    AR_3D,
    MAP_2D
}

private val Context.navDataStore: DataStore<Preferences> by preferencesDataStore(name = "nav_preferences")

class NavigationPreferences(private val context: Context) {

    companion object {
        val KEY_WALKING_MODE = stringPreferencesKey("walking_navigation_mode")
    }

    val walkingModeFlow: Flow<WalkingNavigationMode> = context.navDataStore.data.map { preferences ->
        val savedName = preferences[KEY_WALKING_MODE] ?: WalkingNavigationMode.MAP_2D.name
        try {
            WalkingNavigationMode.valueOf(savedName)
        } catch (_: Exception) {
            WalkingNavigationMode.MAP_2D
        }
    }

    suspend fun setWalkingMode(mode: WalkingNavigationMode) {
        context.navDataStore.edit { preferences ->
            preferences[KEY_WALKING_MODE] = mode.name
        }
    }
}
