package com.example.util

import android.util.Log
import com.example.BuildConfig
import java.util.Locale

/**
 * Privacy-preserving logging utility for VividScout.
 *
 * Security & Privacy Enforcements:
 * 1. Emits log output ONLY in debug builds (BuildConfig.DEBUG == true).
 * 2. Anonymizes user UIDs by keeping only the first 4 characters (e.g. "a1b2***").
 * 3. Sanitizes precise GPS coordinates by rounding to 2 decimal places (e.g. 38.711234 -> 38.71),
 *    providing city-level vicinity accuracy without exposing exact user location history.
 */
object SafeLog {

    /**
     * Anonymizes a Firebase Auth UID or user identifier, retaining only the first 4 characters.
     */
    fun sanitizeUid(uid: String?): String {
        if (uid.isNullOrBlank()) return "anon"
        return if (uid.length <= 4) "${uid}***" else "${uid.take(4)}***"
    }

    /**
     * Rounds latitude or longitude coordinates to 2 decimal places (~1.1 km precision).
     */
    fun sanitizeCoordinate(coord: Double): String {
        return String.format(Locale.US, "%.2f", coord)
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
