package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

private const val TAG = "AppCheckInit"

/**
 * Initializes and configures Firebase App Check for AI Logic attestation.
 * Debug builds use DebugAppCheckProviderFactory (relying on adb-friendly standard token logging).
 * Release builds use PlayIntegrityAppCheckProviderFactory for attestation.
 */
fun configureAppCheck(context: Context) {
    try {
        val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseApp.getInstance()
        } else {
            FirebaseApp.initializeApp(context)
        }

        if (app == null) {
            Log.d(TAG, "FirebaseApp is not configured; skipping App Check initialization.")
            return
        }

        val appCheck = FirebaseAppCheck.getInstance(app)
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed for Release build.")
        }
    } catch (e: Exception) {
        Log.d(TAG, "Firebase App Check initialization skipped: ${e.message}")
    }
}
