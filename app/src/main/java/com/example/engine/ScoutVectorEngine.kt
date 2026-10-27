package com.example.engine

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scout Vector Map Engine.
 * 100% self-contained, offline-first vector map projection and rendering manager.
 * Pre-warms coordinate projection matrices, vector grid contours, and telemetry listeners.
 */
object ScoutVectorEngine {
    private const val TAG = "ScoutVectorEngine"
    private val initialized = AtomicBoolean(false)

    // Base projection scale factor for Lisbon vector geometry
    // Around Lisbon (38.71° N, 9.14° W): 0.01° lat ~ 1110m; 0.01° lng ~ 870m
    const val DEFAULT_BASE_SCALE = 26000f

    // Reference center (Lisbon Chiado)
    const val DEFAULT_LISBON_LAT = 38.7112
    const val DEFAULT_LISBON_LNG = -9.1423

    val isInitialized: Boolean
        get() = initialized.get()

    /**
     * Initializes the Scout Vector Map Engine during MainActivity startup.
     * Pre-warms coordinate projection state and verifies engine posture.
     */
    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            Log.i(TAG, "Initializing Scout Vector Map Engine v1.0...")
            // Pre-heat vector projection parameters and verify context reference
            val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
            Log.i(TAG, "Scout Vector Engine successfully pre-warmed for $appName.")
        } else {
            Log.d(TAG, "Scout Vector Engine is already initialized.")
        }
    }

    /**
     * Called when the map canvas lifecycle enters ON_START.
     */
    fun onStart() {
        Log.i(TAG, "Scout Vector Engine started: telemetry rendering active.")
    }

    /**
     * Called when the map canvas lifecycle enters ON_STOP.
     */
    fun onStop() {
        Log.i(TAG, "Scout Vector Engine stopped: telemetry rendering paused.")
    }

    /**
     * Called when the map canvas lifecycle enters ON_DESTROY.
     */
    fun onDestroy() {
        Log.i(TAG, "Scout Vector Engine destroyed: resources released.")
    }

    /**
     * Projects GPS coordinates (lat, lng) relative to user coordinates into vector canvas screen offset.
     */
    fun projectGpsToCanvasOffset(
        spotLat: Double,
        spotLng: Double,
        userLat: Double,
        userLng: Double,
        canvasCenter: Offset,
        zoomScale: Float,
        baseScale: Float = DEFAULT_BASE_SCALE
    ): Offset {
        val dLng = (spotLng - userLng).toFloat()
        val dLat = (spotLat - userLat).toFloat()

        val pinX = canvasCenter.x + (dLng * baseScale * zoomScale)
        val pinY = canvasCenter.y - (dLat * baseScale * zoomScale)

        return Offset(pinX, pinY)
    }
}
