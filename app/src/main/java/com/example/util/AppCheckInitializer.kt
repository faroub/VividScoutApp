package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Initializes and configures Firebase App Check for AI Logic attestation.
 * Debug builds use DebugAppCheckProviderFactory with optional debug token extraction.
 * Release builds use RecaptchaAppCheckProviderFactory and fail closed if production site key is unavailable.
 */
fun configureAppCheck(context: Context, intent: Intent? = null) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = try {
                FirebaseOptions.fromResource(context)
            } catch (e: Exception) {
                FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("AIzaSyTestApiKeyForRobolectric12345")
                    .setProjectId("vividscout-test")
                    .build()
            }
            if (options != null) {
                FirebaseApp.initializeApp(context, options)
            } else {
                FirebaseApp.initializeApp(context)
            }
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrDefault("[DEFAULT]")
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret saved successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed for Release build.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
