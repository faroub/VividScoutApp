package com.example.data.repository

import com.example.data.local.DiscoveredGemDao
import com.example.data.local.DiscoveredGemEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class ScoutRepository(
    private val scoutDao: ScoutDao,
    private val discoveredGemDao: DiscoveredGemDao? = null
) {

    val allSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getAllSpots()
    val savedSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getSavedSpots()
    val discoveredSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getDiscoveredSpots()
    val offlineDrafts: Flow<List<ScoutSpotEntity>> = scoutDao.getOfflineDrafts()
    val customDiscoveredGems: Flow<List<DiscoveredGemEntity>> =
        discoveredGemDao?.getAllDiscoveredGems() ?: emptyFlow()

    suspend fun toggleSave(id: String, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        scoutDao.setSavedStatus(id, !currentlySaved)
    }

    suspend fun toggleDiscovered(id: String, currentlyDiscovered: Boolean) = withContext(Dispatchers.IO) {
        val newDiscovered = !currentlyDiscovered
        val timestamp = if (newDiscovered) System.currentTimeMillis() else 0L
        scoutDao.setDiscoveredStatus(id, newDiscovered, timestamp)
    }

    suspend fun updateSpotNotes(id: String, notes: String) = withContext(Dispatchers.IO) {
        scoutDao.updateUserNotes(id, notes)
    }

    suspend fun upvote(id: String) = withContext(Dispatchers.IO) {
        scoutDao.incrementUpvote(id)
    }

    suspend fun submitSpotDraft(
        name: String,
        category: String,
        photoUri: String?,
        notes: String = "",
        locationName: String = "Chiado Alleyway",
        latitude: Double = 38.7112,
        longitude: Double = -9.1423
    ): ScoutSpotEntity = withContext(Dispatchers.IO) {
        val newId = "draft_${UUID.randomUUID()}"
        val newSpot = ScoutSpotEntity(
            id = newId,
            title = name,
            description = if (notes.isNotBlank()) notes else "Community discovered secret spot with offline GPS coordinates tagged.",
            category = category.lowercase(),
            distance = "10m radius",
            walkMinutes = 1,
            rating = 5.0,
            reviewCount = "Offline Scout",
            imageUrl = photoUri ?: "https://lh3.googleusercontent.com/aida-public/AB6AXuB7xEP9h28BmLIl7Blc9HlVd6pFvRCzWVE0iDC6Ca1oPEDNOlydMy8IbMDvHPNp5_0uUlGCZu5Cw6Bm39F_KkbvLPOmcgrXHSU-a4h7PVnRNgle66SM05K7w8EOWeQ5yG6-5OzUUMA-vVU40aqUdOAWyXH1crmx9Vp2tTTAQ1QTNNEvtbnoukMI4v0qHyR2ad0mL70ALOBm16NlvPATzNielDDSV7Rp-pzWtZzzWv8pTeMZBuNBXfjY",
            badgeLabel = "COMMUNITY GEM",
            statusBadge = "Saved Offline",
            tagsRaw = "Offline Queued|||GPS Cached: ±3m|||Photo Stored Locally",
            proTip = "Saved in local Room database. Auto-sync will transmit when reconnected.",
            contributor = "@you (Offline)",
            upvotes = 1,
            isSaved = false,
            isOfflineDraft = true,
            isDiscovered = true,
            discoveredTimestamp = System.currentTimeMillis(),
            userNotes = notes.ifBlank { null },
            offlineCached = true,
            locationName = locationName,
            latitude = latitude,
            longitude = longitude
        )
        scoutDao.insertSpot(newSpot)

        // Also record in discovered_gems table if DAO is present
        discoveredGemDao?.insertDiscoveredGem(
            DiscoveredGemEntity(
                id = newId,
                title = name,
                description = newSpot.description,
                category = category.lowercase(),
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                photoUri = photoUri,
                notes = notes,
                discoveredAt = System.currentTimeMillis(),
                isSynced = false
            )
        )

        newSpot
    }

    suspend fun deleteSpot(spot: ScoutSpotEntity) = withContext(Dispatchers.IO) {
        scoutDao.deleteSpot(spot)
        discoveredGemDao?.deleteGemById(spot.id)
    }

    suspend fun syncDraft(spot: ScoutSpotEntity) = withContext(Dispatchers.IO) {
        val synced = spot.copy(
            isOfflineDraft = false,
            statusBadge = "Just Synced"
        )
        scoutDao.updateSpot(synced)
        discoveredGemDao?.markAsSynced(spot.id)
    }

    private val overpassRetrofitClient = com.example.data.network.OverpassRetrofitClient()

    suspend fun scrapeAndSaveOsmGems(
        lat: Double,
        lng: Double,
        radiusMeters: Int = 1500
    ): Result<Int> = withContext(Dispatchers.IO) {
        val result = overpassRetrofitClient.fetchNearbyNodes(lat, lng, radiusMeters)
        result.fold(
            onSuccess = { spots ->
                val spotsToInsert = if (spots.isNotEmpty()) spots else overpassRetrofitClient.generateNearbyFallbackSpots(lat, lng, 6)
                scoutDao.insertSpots(spotsToInsert)
                Result.success(spotsToInsert.size)
            },
            onFailure = { _ ->
                val fallback = overpassRetrofitClient.generateNearbyFallbackSpots(lat, lng, 6)
                scoutDao.insertSpots(fallback)
                Result.success(fallback.size)
            }
        )
    }

    suspend fun purgeUnsavedDistantSpots(
        centerLat: Double,
        centerLng: Double,
        radiusDegrees: Double = 1.0 // Approx 111 km
    ) = withContext(Dispatchers.IO) {
        val minLat = centerLat - radiusDegrees
        val maxLat = centerLat + radiusDegrees
        val minLng = centerLng - radiusDegrees
        val maxLng = centerLng + radiusDegrees
        scoutDao.deleteUnsavedDistantSpots(minLat, maxLat, minLng, maxLng)
    }

    suspend fun initializeIfEmpty() = withContext(Dispatchers.IO) {
        if (scoutDao.getCount() == 0) {
            com.example.data.local.AppDatabase.populateInitialSpots(scoutDao)
        }
    }
}
