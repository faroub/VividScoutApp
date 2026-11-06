package com.example.data.places

/**
 * Abstraction interface for external touristic place data providers.
 *
 * [sourceId] is the canonical provider identifier used by the multi-source
 * orchestration (and by PlaceRepository filtering), e.g. "open_trip_map".
 * Each concrete source maps to exactly one authoritative implementation.
 */
interface PlaceSource {
    val sourceId: String
    suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>>
}
