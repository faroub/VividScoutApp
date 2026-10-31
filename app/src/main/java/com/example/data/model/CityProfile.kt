package com.example.data.model

import com.example.engine.ElevationModel
import com.example.engine.LisbonElevationModel
import com.example.engine.NullElevationModel
import com.example.util.EmptyLandmarkCatalog
import com.example.util.LandmarkCatalog
import com.example.util.LisbonLandmarkCatalog

/**
 * Encapsulates city-specific profile details, including elevation models,
 * curated landmark registries, and feature availability flags.
 */
data class CityProfile(
    val id: String,
    val name: String,
    val country: String,
    val lat: Double,
    val lng: Double,
    val elevationModel: ElevationModel = NullElevationModel,
    val landmarkCatalog: LandmarkCatalog = EmptyLandmarkCatalog,
    val isCuratedSupported: Boolean = false
)

object CityProfileRegistry {

    val LISBON_PROFILE = CityProfile(
        id = "city_lisbon",
        name = "Lisbon",
        country = "Portugal",
        lat = 38.7112,
        lng = -9.1423,
        elevationModel = LisbonElevationModel,
        landmarkCatalog = LisbonLandmarkCatalog,
        isCuratedSupported = true
    )

    fun getProfileForCity(cityName: String, lat: Double = 0.0, lng: Double = 0.0): CityProfile {
        val trimmedName = cityName.trim()
        val lower = trimmedName.lowercase()
        return if (lower.contains("lisbon") || lower.contains("lisboa")) {
            LISBON_PROFILE
        } else {
            val namePart = trimmedName.substringBefore(",").trim().ifBlank { "Selected City" }
            val countryPart = trimmedName.substringAfter(",", "").trim()
            CityProfile(
                id = "city_${namePart.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
                name = namePart,
                country = countryPart,
                lat = lat,
                lng = lng,
                elevationModel = NullElevationModel,
                landmarkCatalog = EmptyLandmarkCatalog,
                isCuratedSupported = false
            )
        }
    }
}
