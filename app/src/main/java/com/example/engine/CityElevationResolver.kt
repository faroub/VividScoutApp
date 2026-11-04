package com.example.engine

/**
 * Resolves regional topography elevation models for supported cities.
 * Returns LisbonElevationModel ONLY for Lisbon / Sintra region, and null for unsupported cities.
 */
object CityElevationResolver {

    fun resolveModelForCity(cityName: String?, originLat: Double = 0.0, originLng: Double = 0.0): ElevationModel? {
        if (cityName.isNullOrBlank()) {
            // Check coordinates if city name is empty: Lisbon bounding box ~ 38.65..38.85 N, -9.45..-9.05 W
            return if (originLat in 38.65..38.85 && originLng in -9.45..-9.05) {
                LisbonElevationModel
            } else {
                null
            }
        }

        val nameLower = cityName.lowercase()
        return when {
            nameLower.contains("lisbon") || nameLower.contains("lisboa") ||
            nameLower.contains("sintra") || nameLower.contains("alfama") ||
            nameLower.contains("chiado") || nameLower.contains("belém") ||
            nameLower.contains("belem") -> LisbonElevationModel
            else -> null // Unsupported cities return null (no fake elevation chart produced!)
        }
    }
}
