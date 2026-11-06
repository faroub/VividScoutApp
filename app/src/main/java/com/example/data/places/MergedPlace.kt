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
) {
    /**
     * The canonical provider ids that contributed to this merged place, derived from
     * its attributions (blank provider ids of legacy records never count).
     */
    val providerIds: Set<String>
        get() = sources.mapNotNull { it.providerId.takeIf { id -> id.isNotBlank() } }.toSet()
}

data class PlaceSourceAttribution(
    val sourceName: String,
    val sourceId: String,
    /** Canonical PROVIDER identifier of the contributor (e.g. "open_trip_map"). */
    val providerId: String = "",
    val sourceUrl: String? = null,
    val license: String? = null
)
