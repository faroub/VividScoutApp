package com.example.data.network

import com.example.data.local.ScoutSpotEntity
import com.example.util.Geo
import com.example.util.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service providing editorially curated municipal places (public parks, landmarks,
 * and heritage sites) for supported cities.
 *
 * No documented municipal open-data API is available for these records: the entries
 * below are an honest editorial selection, not official dataset exports, and carry no
 * dataset record IDs or municipal licenses. Place coordinates were verified against
 * OpenStreetMap (Nominatim) and images against live Wikimedia Commons files, with the
 * attribution and license recorded per entry. Cities without curated coverage return
 * an empty result.
 */
open class MunicipalOpenDataService {

    companion object {
        private const val TAG = "MunicipalOpenDataService"

        // Editorially curated Lisbon places. Coordinates verified against
        // OpenStreetMap (Nominatim); images are real, verified Wikimedia Commons files.
        private val LISBON_CURATED_PLACES = listOf(
            ScoutSpotEntity(
                id = "curated_lisbon_sao_vicente_azulejos",
                title = "São Vicente de Fora Azulejo Cloisters",
                description = "Editorially curated Lisbon landmark: the church and former monastery of São Vicente de Fora, whose cloisters hold one of the city's largest azulejo collections.",
                category = "history",
                distance = "0 m away",
                walkMinutes = 0,
                rating = null, // Never invent ratings
                reviewCount = null, // Never invent review counts
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/View_of_Igreja_e_Mosteiro_de_S%C3%A3o_Vicente_de_Fora_from_Miradouro_das_Portas_do_Sol.JPG/1280px-View_of_Igreja_e_Mosteiro_de_S%C3%A3o_Vicente_de_Fora_from_Miradouro_das_Portas_do_Sol.JPG",
                badgeLabel = "Editorial Curation",
                statusBadge = "Curated Lisbon Spot",
                tagsRaw = "curated|||azulejo|||history|||monastery",
                proTip = "Editorially curated Lisbon place; coordinates verified against OpenStreetMap. Image: José Luiz, CC BY-SA 3.0, via Wikimedia Commons.",
                contributor = "VividScout Editorial",
                latitude = 38.7147286,
                longitude = -9.1274895,
                locationName = "São Vicente, Lisboa"
            ),
            ScoutSpotEntity(
                id = "curated_lisbon_cerca_da_graca",
                title = "Jardim da Cerca da Graça Public Viewpoint Park",
                description = "Editorially curated Lisbon landmark: civic hillside viewpoint park in Graça with shaded terraces overlooking the city.",
                category = "sights",
                distance = "0 m away",
                walkMinutes = 0,
                rating = null, // Never invent ratings
                reviewCount = null, // Never invent review counts
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Jardim_da_Cerca_da_Gra%C3%A7a_Lisboa_01.jpg/1280px-Jardim_da_Cerca_da_Gra%C3%A7a_Lisboa_01.jpg",
                badgeLabel = "Editorial Curation",
                statusBadge = "Curated Lisbon Spot",
                tagsRaw = "curated|||park|||viewpoint",
                proTip = "Editorially curated Lisbon place; coordinates verified against OpenStreetMap. Image: GualdimG, CC BY-SA 4.0, via Wikimedia Commons.",
                contributor = "VividScout Editorial",
                latitude = 38.7173184,
                longitude = -9.1319531,
                locationName = "Graça, Lisboa"
            )
        )
    }

    /**
     * Returns editor-curated places for the requested city, filtered by exact geodesic
     * distance from the user. Returns an empty list for cities without curated coverage,
     * for invalid coordinates, and for non-positive radii.
     */
    open suspend fun fetchMunicipalDataSpots(
        cityName: String = "Lisbon",
        userLat: Double = 38.7112,
        userLng: Double = -9.1423,
        radiusMeters: Int = 3000
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        // Validate input coordinates
        if (userLat.isNaN() || !userLat.isFinite() || userLng.isNaN() || !userLng.isFinite() ||
            userLat < -90.0 || userLat > 90.0 || userLng < -180.0 || userLng > 180.0 ||
            (userLat == 0.0 && userLng == 0.0)) {
            SafeLog.w(TAG, "Invalid coordinates provided: lat=${SafeLog.sanitizeCoordinate(userLat)}, lng=${SafeLog.sanitizeCoordinate(userLng)}")
            return@withContext emptyList()
        }

        if (radiusMeters <= 0) {
            SafeLog.w(TAG, "Invalid radius provided: $radiusMeters m")
            return@withContext emptyList()
        }

        val isLisbonArea = cityName.contains("Lisbon", ignoreCase = true) ||
            cityName.contains("Lisboa", ignoreCase = true) ||
            (userLat in 38.65..38.80 && userLng in -9.30..-9.00)

        if (!isLisbonArea) {
            SafeLog.i(TAG, "No curated places available for city '$cityName'. Returning empty result.")
            return@withContext emptyList()
        }

        // Filter curated Lisbon places by exact geodesic distance
        LISBON_CURATED_PLACES.filter { spot ->
            val distMeters = Geo.calculateDistanceMeters(userLat, userLng, spot.latitude, spot.longitude)
            !distMeters.isNaN() && distMeters <= radiusMeters
        }.map { spot ->
            val distMeters = Geo.calculateDistanceMeters(userLat, userLng, spot.latitude, spot.longitude)
            spot.copy(
                distance = Geo.formatDistance(distMeters),
                walkMinutes = Geo.calculateWalkMinutes(distMeters)
            )
        }
    }
}
