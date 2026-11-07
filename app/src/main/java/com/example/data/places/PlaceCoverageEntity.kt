package com.example.data.places

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording what one provider has been successfully asked for: the query
 * center, the query radius, and when the answer arrived.
 *
 * The presence of one fresh cached PLACE with a provider's attribution does NOT prove
 * that the provider was queried over the full requested area (a larger radius or a
 * shifted center would silently reuse an incomplete cache), so coverage is recorded
 * explicitly — including for successful EMPTY results, which prove the provider
 * answered "no places here" for that area.
 *
 * A row is written ONLY after an actual provider success. A provider failure never
 * marks a region as covered, so a failed request never becomes an authoritative
 * "no places here" answer.
 */
@Entity(
    tableName = "place_coverage",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["fetchedAt"])
    ]
)
data class PlaceCoverageEntity(
    @PrimaryKey
    val providerId: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Int,
    val status: String,
    val fetchedAt: Long
)
