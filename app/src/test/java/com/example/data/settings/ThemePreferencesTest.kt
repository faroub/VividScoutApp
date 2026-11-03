package com.example.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemePreferencesTest {

    @Test
    fun themePreferences_defaultModeIsSystem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ThemePreferences(context)

        val mode = prefs.themeModeFlow.first()
        assertEquals(AppThemeMode.SYSTEM, mode)
    }

    @Test
    fun themePreferences_setThemeModeDark_persistsAndReturnsDark() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ThemePreferences(context)

        prefs.setThemeMode(AppThemeMode.DARK)

        val updatedMode = prefs.themeModeFlow.first()
        assertEquals(AppThemeMode.DARK, updatedMode)
    }

    @Test
    fun themePreferences_setThemeModeLight_persistsAndReturnsLight() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ThemePreferences(context)

        prefs.setThemeMode(AppThemeMode.LIGHT)

        val updatedMode = prefs.themeModeFlow.first()
        assertEquals(AppThemeMode.LIGHT, updatedMode)
    }
}
