package com.example.data.network

/**
 * Single source of truth for Firebase AI Logic Gemini Model IDs.
 *
 * Primary Model: gemini-3.8-flash (Primary recommended fast model for Gemini API)
 * Secondary Model: gemini-3.5-flash (Supported fallback model)
 *
 * Documentation References:
 * - https://developer.android.com/ai/gemini/index
 * - https://firebase.google.com/docs/ai-logic/get-started
 */
object GeminiModelConstants {
    /** Primary recommended fast Gemini model for high-throughput AI spot discovery */
    const val PRIMARY_MODEL_ID = "gemini-3.8-flash"

    /** Secondary fallback model ID if primary model request fails or times out */
    const val SECONDARY_MODEL_ID = "gemini-3.5-flash"
}
