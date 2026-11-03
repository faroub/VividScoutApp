package com.example.data.places

/**
 * Represents a deduplicated, multi-source merged place candidate.
 */
data class MergedPlace(
    val primaryName: String,
    val primaryDescription: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val license: String? = null,
    val languageCode: String = "en",
    val sources: List<PlaceSourceAttribution>
)

data class PlaceSourceAttribution(
    val sourceName: String,
    val sourceId: String,
    val sourceUrl: String? = null,
    val license: String? = null
)
