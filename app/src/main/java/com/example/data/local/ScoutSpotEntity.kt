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
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val imageUrl: String,
    val badgeLabel: String,
    val statusBadge: String,
    val tagsRaw: String, // comma-separated tags
    val proTip: String? = null,
    val contributor: String? = null,
    val contributorAvatar: String? = null,
    val upvotes: Int? = null,
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

    val openingHours: String?
        get() = tagsRaw.split("|||").find { it.startsWith("opening_hours=") }?.removePrefix("opening_hours=")

    val bestSeason: String?
        get() {
            val seasonTag = tagsRaw.split("|||").find { it.startsWith("season=") || it.startsWith("best_season=") }
            if (seasonTag != null) return seasonTag.substringAfter("=").trim()

            val textToSearch = "$description ${proTip ?: ""}".lowercase()
            return when {
                textToSearch.contains("spring") -> "Spring"
                textToSearch.contains("summer") -> "Summer"
                textToSearch.contains("autumn") || textToSearch.contains("fall") -> "Autumn"
                textToSearch.contains("winter") -> "Winter"
                else -> null
            }
        }
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

/**
 * True for the illustrative "Offline Demo Spot" records that [com.example.data.network.OverpassRetrofitClient.generateNearbyFallbackSpots]
 * synthesizes around the user's coordinates when every Overpass mirror fails or returns
 * zero elements. Derived from the id prefix assigned at generation time — the stable
 * machine marker (the current `fallback_` prefix, and the legacy `osm_fallback_` prefix
 * still present on rows persisted before the id scheme changed) — so no schema change or
 * migration is needed and rows already on existing installs classify correctly at read
 * time. Demo records must never be presented as verified real-world places, published
 * as community submissions, or included in shared trips as real destinations.
 */
val ScoutSpotEntity.isDemoFallbackSpot: Boolean
    get() = id.startsWith("osm_fallback_") || id.startsWith("fallback_")

fun ScoutSpotEntity.computedDistanceString(userLat: Double, userLng: Double): String {
    val meters = computedDistanceMeters(userLat, userLng)
    return com.example.data.location.LocationTracker.formatDistance(meters)
}

fun ScoutSpotEntity.computedWalkMinutes(userLat: Double, userLng: Double): Int {
    val meters = computedDistanceMeters(userLat, userLng)
    return com.example.data.location.LocationTracker.calculateWalkMinutes(meters)
}

