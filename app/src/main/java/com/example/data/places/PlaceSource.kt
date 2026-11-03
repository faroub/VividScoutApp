package com.example.data.places

/**
 * Abstraction interface for external touristic place data providers.
 */
interface PlaceSource {
    suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>>
}
