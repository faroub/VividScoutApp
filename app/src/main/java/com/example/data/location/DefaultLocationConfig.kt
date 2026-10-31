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

    val defaultLocation: Location
        get() = Location("DefaultFallbackProvider").apply {
            latitude = DEFAULT_LAT
            longitude = DEFAULT_LNG
            accuracy = 100f
        }
}
