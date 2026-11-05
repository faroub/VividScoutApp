package com.example.util

import com.example.util.SafeLog
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
            SafeLog.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            SafeLog.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                SafeLog.w(tag, message, throwable)
            } else {
                SafeLog.w(tag, message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                SafeLog.e(tag, message, throwable)
            } else {
                SafeLog.e(tag, message)
            }
        }
    }
}
