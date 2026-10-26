package com.example.data.repository

import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ScoutRepository(private val scoutDao: ScoutDao) {

    val allSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getAllSpots()
    val savedSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getSavedSpots()
    val offlineDrafts: Flow<List<ScoutSpotEntity>> = scoutDao.getOfflineDrafts()

    suspend fun toggleSave(id: String, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        scoutDao.setSavedStatus(id, !currentlySaved)
    }

    suspend fun upvote(id: String) = withContext(Dispatchers.IO) {
        scoutDao.incrementUpvote(id)
    }

    suspend fun submitSpotDraft(
        name: String,
        category: String,
        photoUri: String?,
        locationName: String = "Chiado Alleyway",
        latitude: Double = 38.7112,
        longitude: Double = -9.1423
    ): ScoutSpotEntity = withContext(Dispatchers.IO) {
        val newSpot = ScoutSpotEntity(
            id = "draft_${UUID.randomUUID()}",
            title = name,
            description = "Community discovered secret spot with offline GPS coordinates tagged.",
            category = category.lowercase(),
            distance = "10m radius",
            walkMinutes = 1,
            rating = 5.0,
            reviewCount = "New",
            imageUrl = photoUri ?: "https://lh3.googleusercontent.com/aida-public/AB6AXuB7xEP9h28BmLIl7Blc9HlVd6pFvRCzWVE0iDC6Ca1oPEDNOlydMy8IbMDvHPNp5_0uUlGCZu5Cw6Bm39F_KkbvLPOmcgrXHSU-a4h7PVnRNgle66SM05K7w8EOWeQ5yG6-5OzUUMA-vVU40aqUdOAWyXH1crmx9Vp2tTTAQ1QTNNEvtbnoukMI4v0qHyR2ad0mL70ALOBm16NlvPATzNielDDSV7Rp-pzWtZzzWv8pTeMZBuNBXfjY",
            badgeLabel = "COMMUNITY GEM",
            statusBadge = "Offline Ready",
            tagsRaw = "Offline Queued|||GPS Cached: ±3m|||Photo Stored Locally",
            proTip = "Will transmit high-res photo when on Wi-Fi/4G.",
            contributor = "@you (Offline)",
            upvotes = 1,
            isSaved = false,
            isOfflineDraft = true,
            locationName = locationName,
            latitude = latitude,
            longitude = longitude
        )
        scoutDao.insertSpot(newSpot)
        newSpot
    }

    suspend fun syncDraft(spot: ScoutSpotEntity) = withContext(Dispatchers.IO) {
        val synced = spot.copy(
            isOfflineDraft = false,
            statusBadge = "Just Synced"
        )
        scoutDao.updateSpot(synced)
    }

    suspend fun initializeIfEmpty() = withContext(Dispatchers.IO) {
        if (scoutDao.getCount() == 0) {
            com.example.data.local.AppDatabase.populateInitialSpots(scoutDao)
        }
    }
}
