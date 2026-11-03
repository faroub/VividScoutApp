package com.example.data.places

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing cached merged places with tile keys and fetch timestamps.
 */
@Entity(
    tableName = "cached_places",
    indices = [
        Index(value = ["tileKey"]),
        Index(value = ["fetchedTimestamp"])
    ]
)
data class CachedPlaceEntity(
    @PrimaryKey
    val placeId: String,
    val primaryName: String,
    val primaryDescription: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val license: String? = null,
    val languageCode: String = "en",
    val sourcesJson: String, // Serialized list of PlaceSourceAttribution
    val tileKey: String, // 5-char geohash
    val fetchedTimestamp: Long = System.currentTimeMillis()
)
