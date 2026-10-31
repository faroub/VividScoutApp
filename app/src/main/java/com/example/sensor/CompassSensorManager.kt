package com.example.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CompassAccuracy {
    HIGH,
    MEDIUM,
    LOW,
    UNCALIBRATED,
    UNKNOWN
}

data class CompassState(
    val azimuthDegrees: Float = 0f,
    val declinationDegrees: Float = 0f,
    val accuracyStatus: CompassAccuracy = CompassAccuracy.UNKNOWN,
    val isSensorAvailable: Boolean = true,
    val isTrueNorthMode: Boolean = true
) {
    /**
     * Effective heading in degrees (0..360).
     * If isTrueNorthMode is true, includes geomagnetic declination adjustment.
     */
    val effectiveHeading: Float
        get() = if (isTrueNorthMode) {
            (azimuthDegrees + declinationDegrees + 360f) % 360f
        } else {
            (azimuthDegrees + 360f) % 360f
        }
}

/**
     * Offline Sensor Listener for device compass sensors (Rotation Vector / Magnetometer + Accelerometer).
     * Calculates magnetic azimuth and converts to True North using GeomagneticField offline.
     */
class CompassSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _compassState = MutableStateFlow(CompassState(isSensorAvailable = sensorManager != null))
    val compassState: StateFlow<CompassState> = _compassState.asStateFlow()

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var currentAzimuth = 0f

    /**
     * Starts monitoring sensors. Computes declination if GPS coordinates are provided.
     */
    fun startListening(userLat: Double? = null, userLng: Double? = null) {
        if (sensorManager == null) {
            _compassState.value = _compassState.value.copy(isSensorAvailable = false)
            return
        }

        // Calculate magnetic declination for True North adjustment
        if (userLat != null && userLng != null) {
            try {
                val geoField = GeomagneticField(
                    userLat.toFloat(),
                    userLng.toFloat(),
                    0f,
                    System.currentTimeMillis()
                )
                val declination = geoField.declination
                _compassState.value = _compassState.value.copy(declinationDegrees = declination)
            } catch (e: Exception) {
                // Fallback to 0 if calculation fails
            }
        }

        var registered = false
        if (rotationVectorSensor != null) {
            registered = sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        if (!registered && accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
            registered = true
        }

        _compassState.value = _compassState.value.copy(isSensorAvailable = registered)
    }

    /**
     * Unregisters sensor listeners to preserve battery lifecycle.
     */
    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Toggles between True North orientation and Magnetic North orientation.
     */
    fun toggleTrueNorthMode() {
        _compassState.value = _compassState.value.copy(
            isTrueNorthMode = !_compassState.value.isTrueNorthMode
        )
    }

    /**
     * Updates declination angle when user location updates.
     */
    fun updateLocation(userLat: Double, userLng: Double) {
        try {
            val geoField = GeomagneticField(
                userLat.toFloat(),
                userLng.toFloat(),
                0f,
                System.currentTimeMillis()
            )
            _compassState.value = _compassState.value.copy(declinationDegrees = geoField.declination)
        } catch (e: Exception) {
            // Ignore error
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val normalizedAzimuth = (rawAzimuth + 360f) % 360f
                updateAzimuthSmoothly(normalizedAzimuth)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                if (hasGeomagnetic) processAccelMag()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
                if (hasGravity) processAccelMag()
            }
        }
    }

    private fun processAccelMag() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val rawAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalizedAzimuth = (rawAzimuth + 360f) % 360f
            updateAzimuthSmoothly(normalizedAzimuth)
        }
    }

    private fun updateAzimuthSmoothly(targetAzimuth: Float) {
        // Low-pass filter for smooth needle movement
        var diff = targetAzimuth - currentAzimuth
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        currentAzimuth = (currentAzimuth + diff * 0.25f + 360f) % 360f

        _compassState.value = _compassState.value.copy(azimuthDegrees = currentAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val mappedAccuracy = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNCALIBRATED
            else -> CompassAccuracy.UNKNOWN
        }
        _compassState.value = _compassState.value.copy(accuracyStatus = mappedAccuracy)
    }
}
