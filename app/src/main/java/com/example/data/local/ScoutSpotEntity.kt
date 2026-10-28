package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "scout_spots")
data class ScoutSpotEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String, // "sights", "eats", "history", "shortcut"
    val distance: String,
    val walkMinutes: Int,
    val rating: Double,
    val reviewCount: String,
    val imageUrl: String,
    val badgeLabel: String,
    val statusBadge: String,
    val tagsRaw: String, // comma-separated tags
    val proTip: String? = null,
    val contributor: String? = null,
    val contributorAvatar: String? = null,
    val upvotes: Int = 0,
    val isSaved: Boolean = false,
    val isOfflineDraft: Boolean = false,
    val isDiscovered: Boolean = false,
    val discoveredTimestamp: Long? = null,
    val userNotes: String? = null,
    val offlineCached: Boolean = true,
    val locationName: String = "Lisbon",
    val latitude: Double = 38.7112,
    val longitude: Double = -9.1423,
    val audioDuration: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val name: String
        get() = title

    val neighborhood: String
        get() = locationName
}

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString("|||") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split("|||")
    }
}

fun ScoutSpotEntity.computedDistanceMeters(userLat: Double, userLng: Double): Float {
    return com.example.data.location.LocationTracker.distanceBetween(userLat, userLng, latitude, longitude)
}

fun ScoutSpotEntity.computedDistanceString(userLat: Double, userLng: Double): String {
    val meters = computedDistanceMeters(userLat, userLng)
    return com.example.data.location.LocationTracker.formatDistance(meters)
}

fun ScoutSpotEntity.computedWalkMinutes(userLat: Double, userLng: Double): Int {
    val meters = computedDistanceMeters(userLat, userLng)
    return com.example.data.location.LocationTracker.calculateWalkMinutes(meters)
}

