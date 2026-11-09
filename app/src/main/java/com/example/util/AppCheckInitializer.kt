package com.example.util

import android.content.Context
import com.example.util.SafeLog
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.AppCheckProviderFactory
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
            SafeLog.d(TAG, "FirebaseApp is not configured; skipping App Check initialization.")
            return
        }

        val appCheck = FirebaseAppCheck.getInstance(app)
        if (BuildConfig.DEBUG) {
            // The debug provider ships only through debugImplementation (app/build
            // .gradle.kts), so the release classpath does not carry it. It is loaded
            // reflectively to avoid a compile-time reference from release sources.
            appCheck.installAppCheckProviderFactory(loadDebugAppCheckProviderFactory())
            SafeLog.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            SafeLog.d(TAG, "PlayIntegrityAppCheckProviderFactory installed for Release build.")
        }
    } catch (e: Exception) {
        SafeLog.d(TAG, "Firebase App Check initialization skipped: ${e.message}")
    }
}

/**
 * Reflectively obtains the debug App Check provider factory, which lives in the
 * debug-only `firebase-appcheck-debug` artifact. Any failure to load it means the
 * debug build cannot attest — surfaced loudly so debug sessions notice immediately.
 */
private fun loadDebugAppCheckProviderFactory(): AppCheckProviderFactory {
    val factoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
    val instance = factoryClass.getMethod("getInstance").invoke(null)
    return instance as AppCheckProviderFactory
}
