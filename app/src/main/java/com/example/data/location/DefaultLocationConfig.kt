package com.example.data.location

import android.location.Location

/**
 * Single source of truth for global default fallback location coordinates and metadata.
 * Centralizes default coordinates across ViewModels, Repositories, and Services,
 * ensuring fallback coordinates are never silently presented as the user's actual location.
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

    fun setActiveFallbackCity(lat: Double, lng: Double, cityName: String) {
        if (lat != 0.0 && lng != 0.0) {
            activeFallbackLat = lat
            activeFallbackLng = lng
            activeFallbackCityName = cityName
        }
    }

    val defaultLocation: Location
        get() = Location("DefaultFallbackProvider").apply {
            latitude = activeFallbackLat
            longitude = activeFallbackLng
            accuracy = 100f
        }
}
