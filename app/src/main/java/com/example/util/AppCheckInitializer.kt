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
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
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
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
