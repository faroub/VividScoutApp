package com.example.data.sync

import com.example.data.local.ScoutSpotEntity
import java.util.UUID

data class SharedSpotItem(
    val spotId: String,
    val title: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String? = null
)

data class SharedTrip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val spots: List<SharedSpotItem>,
    val createdAt: Long = System.currentTimeMillis(),
    val expireAt: Long = System.currentTimeMillis() + (30L * 24 * 3600 * 1000L) // 30 days TTL
) {
    val shareableLink: String
        get() = "https://vividscout.app/trip/$id"

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expireAt
}

object SharedTripManager {

    const val TTL_DURATION_MS = 30L * 24 * 3600 * 1000L // 30 days

    fun createSharedTrip(
        title: String,
        spots: List<ScoutSpotEntity>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SharedTrip {
        val spotItems = spots.map { spot ->
            SharedSpotItem(
                spotId = spot.id,
                title = spot.title,
                category = spot.category,
                latitude = spot.latitude,
                longitude = spot.longitude,
                imageUrl = spot.imageUrl
            )
        }

        val randomId = UUID.randomUUID().toString()

        return SharedTrip(
            id = randomId,
            title = title,
            spots = spotItems,
            createdAt = currentTimeMs,
            expireAt = currentTimeMs + TTL_DURATION_MS
        )
    }

    fun isTripExpired(trip: SharedTrip, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return currentTimeMs >= trip.expireAt
    }
}
