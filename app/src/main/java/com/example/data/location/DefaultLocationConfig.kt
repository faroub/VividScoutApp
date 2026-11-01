package com.example.data.location

import android.content.Context
import android.location.Location
import android.util.Log

/**
 * Single source of truth for global default fallback location coordinates and metadata.
 * Centralizes default coordinates across ViewModels, Repositories, and Services,
 * providing persistent preference storage and robust coordinate range validation.
 */
object DefaultLocationConfig {
    const val DEFAULT_LAT = 38.7112
    const val DEFAULT_LNG = -9.1423
    const val DEFAULT_CITY_NAME = "Select City"
    const val DEFAULT_LOCATION_LABEL = "Fallback Location"

    @Volatile
    var activeFallbackLat: Double = DEFAULT_LAT
        private set

    @Volatile
    var activeFallbackLng: Double = DEFAULT_LNG
        private set

    @Volatile
    var activeFallbackCityName: String = DEFAULT_CITY_NAME
        private set

    /**
     * Updates active fallback city metadata using range and finiteness validation
     * (permitting valid zero values e.g. Equator / Prime Meridian) and persisting choice.
     */
    fun setActiveFallbackCity(lat: Double, lng: Double, cityName: String, context: Context? = null) {
        if (!lat.isNaN() && !lng.isNaN() && lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0) {
            activeFallbackLat = lat
            activeFallbackLng = lng
            activeFallbackCityName = cityName
            context?.let { ctx ->
                try {
                    val prefs = ctx.getSharedPreferences("vivid_scout_location_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("key_active_city_name", cityName)
                        .putFloat("key_active_city_lat", lat.toFloat())
                        .putFloat("key_active_city_lng", lng.toFloat())
                        .apply()
                } catch (e: Exception) {
                    Log.w("DefaultLocationConfig", "Failed to persist fallback city preference: ${e.message}")
                }
            }
        }
    }

    /**
     * Restores persisted fallback city preference from SharedPreferences on process creation.
     */
    fun initFromPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences("vivid_scout_location_prefs", Context.MODE_PRIVATE)
            val savedCity = prefs.getString("key_active_city_name", null)
            val savedLat = prefs.getFloat("key_active_city_lat", Float.NaN)
            val savedLng = prefs.getFloat("key_active_city_lng", Float.NaN)
            if (!savedCity.isNullOrBlank() && !savedLat.isNaN() && !savedLng.isNaN()) {
                activeFallbackLat = savedLat.toDouble()
                activeFallbackLng = savedLng.toDouble()
                activeFallbackCityName = savedCity
            }
        } catch (e: Exception) {
            Log.w("DefaultLocationConfig", "Failed to restore fallback city preference: ${e.message}")
        }
    }

    val defaultLocation: Location
        get() = Location("DefaultFallbackProvider").apply {
            latitude = activeFallbackLat
            longitude = activeFallbackLng
            accuracy = 100f
        }
}
