package com.example.data.places

/**
 * Result wrapper containing merged places, cache freshness state, and geohash tile key.
 */
data class PlaceRepositoryResult(
    val places: List<MergedPlace>,
    val isStale: Boolean = false,
    val sourceTileKey: String
)
