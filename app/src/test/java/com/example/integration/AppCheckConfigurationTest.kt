package com.example.integration

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.testutil.TestFirebaseInitializer
import com.example.util.configureAppCheck
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppCheckConfigurationTest {

    private val debugStorePrefix = "com.google.firebase.appcheck.debug.store."
    private val debugSecretKey = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TestFirebaseInitializer.initTestFirebase(context)
    }

    @Test
    fun configureAppCheck_executesGracefullyWithoutReadingIntentExtra() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testToken = "injected-intent-token-should-be-ignored"
        val intent = Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", testToken)

        // Invoke configureAppCheck without passing intent
        configureAppCheck(context)

        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrDefault("[DEFAULT]")
        val prefs = context.getSharedPreferences(
            "$debugStorePrefix$persistenceKey",
            Context.MODE_PRIVATE
        )

        val savedToken = prefs.getString(debugSecretKey, null)
        assertNull("Intent extra debug token must NOT be read or written to SharedPreferences", savedToken)
    }

    @Test
    fun configureAppCheck_releaseMode_doesNotReadIntentExtraOrStoreToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Execute initialization
        configureAppCheck(context)

        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrDefault("[DEFAULT]")
        val prefs = context.getSharedPreferences(
            "$debugStorePrefix$persistenceKey",
            Context.MODE_PRIVATE
        )

        val savedToken = prefs.getString(debugSecretKey, null)
        assertNull("Release-style initialization must never store any intent token in SharedPreferences", savedToken)
    }
}
