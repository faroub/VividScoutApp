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
class NavigationPreferencesTest {

    @Test
    fun navigationPreferences_defaultModeIsMap2D() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = NavigationPreferences(context)

        val mode = prefs.walkingModeFlow.first()
        assertEquals(WalkingNavigationMode.MAP_2D, mode)
    }

    @Test
    fun navigationPreferences_setWalkingModeAr3d_persistsAndReturnsAr3d() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = NavigationPreferences(context)

        prefs.setWalkingMode(WalkingNavigationMode.AR_3D)

        val updatedMode = prefs.walkingModeFlow.first()
        assertEquals(WalkingNavigationMode.AR_3D, updatedMode)
    }
}
