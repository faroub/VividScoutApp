package com.example.data.model

import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.data.local.computedDistanceString

/**
 * Domain model representing a hidden gem location rendered on the vector map canvas.
 */
data class ScoutGem(
    val id: String,
    val title: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val description: String = "",
    val distanceText: String = "",
    val distanceMeters: Float = 0f,
    val isDiscovered: Boolean = false,
    val isSaved: Boolean = false,
    val rating: Double = 4.8,
    val imageUrl: String = "",
    val statusBadge: String = "Secret Corner"
)

/**
 * Converts a Room database ScoutSpotEntity into a domain ScoutGem location.
 */
fun ScoutSpotEntity.toScoutGem(userLat: Double = 38.7112, userLng: Double = -9.1423): ScoutGem {
    return ScoutGem(
        id = id,
        title = title,
        category = category,
        latitude = latitude,
        longitude = longitude,
        description = description,
        distanceText = computedDistanceString(userLat, userLng),
        distanceMeters = computedDistanceMeters(userLat, userLng),
        isDiscovered = isDiscovered,
        isSaved = isSaved,
        rating = rating,
        imageUrl = imageUrl,
        statusBadge = statusBadge
    )
}

/**
 * Converts a domain ScoutGem location into a Room database ScoutSpotEntity.
 */
fun ScoutGem.toEntity(): ScoutSpotEntity {
    return ScoutSpotEntity(
        id = id,
        title = title,
        description = description,
        category = category,
        distance = distanceText,
        walkMinutes = 5,
        rating = rating,
        reviewCount = "42 reviews",
        imageUrl = imageUrl,
        badgeLabel = "Secret Corner",
        statusBadge = statusBadge,
        tagsRaw = "scout,hidden,$category",
        isDiscovered = isDiscovered,
        isSaved = isSaved,
        latitude = latitude,
        longitude = longitude
    )
}
