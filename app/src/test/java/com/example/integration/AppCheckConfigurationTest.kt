package com.example.integration

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.util.configureAppCheck
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppCheckConfigurationTest {

    private val debugTokenExtra = "FIREBASE_APPCHECK_DEBUG_TOKEN"
    private val debugStorePrefix = "com.google.firebase.appcheck.debug.store."
    private val debugSecretKey = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    @Test
    fun configureAppCheck_withDebugTokenIntent_storesDebugSecretInSharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testToken = "test-debug-token-guid-abcdef-123456"

        val intent = Intent().apply {
            putExtra(debugTokenExtra, testToken)
        }

        configureAppCheck(context, intent)

        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrDefault("[DEFAULT]")
        val prefs = context.getSharedPreferences(
            "$debugStorePrefix$persistenceKey",
            Context.MODE_PRIVATE
        )

        val savedToken = prefs.getString(debugSecretKey, null)
        assertNotNull("Debug token must be saved to SharedPreferences", savedToken)
        assertEquals(testToken, savedToken)
    }

    @Test
    fun configureAppCheck_withoutIntentExtra_executesGracefullyWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Invoking with null Intent must succeed without throwing exception
        configureAppCheck(context, null)

        // Invoking with empty Intent must also succeed gracefully
        configureAppCheck(context, Intent())
    }
}
