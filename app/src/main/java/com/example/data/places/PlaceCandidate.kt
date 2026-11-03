package com.example.data.places

/**
 * Unified candidate representation for external place data fetched from third-party geospatial APIs
 * (Overpass OpenStreetMap, Wikidata/Wikipedia, OpenTripMap, GeoNames, Europeana, etc.).
 */
data class PlaceCandidate(
    val sourceName: String,
    val sourceId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "sights",
    val description: String = "",
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val license: String? = null,
    val languageCode: String = "en"
)
