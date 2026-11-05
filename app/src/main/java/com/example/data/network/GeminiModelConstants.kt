package com.example.data.network

import com.example.util.SafeLog
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * Single source of truth for Firebase AI Logic Gemini Model IDs and display names.
 * Backed by Firebase Remote Config with default fallback constants.
 *
 * References:
 * - https://developer.android.com/ai/gemini/index
 * - https://firebase.google.com/docs/ai-logic/get-started
 */
object GeminiModelConstants {
    private const val TAG = "GeminiModelConstants"

    const val DEFAULT_PRIMARY_MODEL_ID = "gemini-3.8-flash"
    const val DEFAULT_SECONDARY_MODEL_ID = "gemini-3.5-flash"

    const val REMOTE_CONFIG_PRIMARY_KEY = "gemini_primary_model_id"
    const val REMOTE_CONFIG_SECONDARY_KEY = "gemini_secondary_model_id"

    const val PRIMARY_DISPLAY_NAME = "Gemini 3.8 Flash"
    const val SECONDARY_DISPLAY_NAME = "Gemini 3.5 Flash"

    /**
     * Retrieves the primary model ID from Firebase Remote Config if configured,
     * falling back to DEFAULT_PRIMARY_MODEL_ID ("gemini-3.8-flash").
     */
    fun getPrimaryModelId(): String {
        return try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val remoteValue = remoteConfig.getString(REMOTE_CONFIG_PRIMARY_KEY)
            if (remoteValue.isNotBlank()) remoteValue else DEFAULT_PRIMARY_MODEL_ID
        } catch (e: Exception) {
            SafeLog.d(TAG, "RemoteConfig unavailable, using default primary model ID: ${e.message}")
            DEFAULT_PRIMARY_MODEL_ID
        }
    }

    /**
     * Retrieves the secondary fallback model ID from Firebase Remote Config if configured,
     * falling back to DEFAULT_SECONDARY_MODEL_ID ("gemini-3.5-flash").
     */
    fun getSecondaryModelId(): String {
        return try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val remoteValue = remoteConfig.getString(REMOTE_CONFIG_SECONDARY_KEY)
            if (remoteValue.isNotBlank()) remoteValue else DEFAULT_SECONDARY_MODEL_ID
        } catch (e: Exception) {
            SafeLog.d(TAG, "RemoteConfig unavailable, using default secondary model ID: ${e.message}")
            DEFAULT_SECONDARY_MODEL_ID
        }
    }

    /**
     * Backwards compatibility getters
     */
    val PRIMARY_MODEL_ID: String
        get() = getPrimaryModelId()

    val SECONDARY_MODEL_ID: String
        get() = getSecondaryModelId()
}
