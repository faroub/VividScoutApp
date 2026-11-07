package com.example.data.repository

import com.example.data.local.DiscoveredGemDao
import com.example.data.local.DiscoveredGemEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.UserPointsDao
import com.example.data.local.UserPointsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.data.local.AchievementDao
import com.example.data.local.AchievementEntity
import com.example.data.location.DefaultLocationConfig
import com.example.util.SafeLog

open class ScoutRepository(
    private val scoutDao: ScoutDao,
    private val discoveredGemDao: DiscoveredGemDao? = null,
    private val achievementDao: AchievementDao? = null,
    private val context: android.content.Context? = null,
    private val userPointsDao: UserPointsDao? = null
) {

    companion object {
        private const val TAG = "ScoutRepository"

        // Scout Points awarded for a photo-verified spot submission. This mirrors the
        // "+15 Scout Points" bonus label shown next to the photo-verification dropzone
        // (see scoutPointsBonus in Localization.kt); keep the two in sync.
        const val POINTS_PER_PHOTO_VERIFIED_SPOT = 15
    }

    private val achievementEvaluator = achievementDao?.let { AchievementEvaluator(scoutDao, it) }

    val allSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getAllSpots()
    val savedSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getSavedSpots()
    val discoveredSpots: Flow<List<ScoutSpotEntity>> = scoutDao.getDiscoveredSpots()
    val offlineDrafts: Flow<List<ScoutSpotEntity>> = scoutDao.getOfflineDrafts()
    val customDiscoveredGems: Flow<List<DiscoveredGemEntity>> =
        discoveredGemDao?.getAllDiscoveredGems() ?: emptyFlow()
    val allAchievements: Flow<List<AchievementEntity>> =
        achievementDao?.getAllAchievements() ?: emptyFlow()
    val unlockedAchievementsCount: Flow<Int> =
        achievementDao?.getUnlockedCount() ?: emptyFlow()
    // Scout Points are derived only from the persisted user_points ledger (sum of all
    // awards), so the displayed total reflects real user activity, survives restarts,
    // and stays correct offline. The legacy hardcoded placeholder (150 for everyone)
    // is gone. Users without any awards read 0.
    val userPoints: Flow<Int> = userPointsDao?.observeTotalPoints()
        ?: kotlinx.coroutines.flow.flowOf(0)

    suspend fun getAllSpotsSync(): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        scoutDao.getAllSpotsSync()
    }

    suspend fun clearCachedData() = withContext(Dispatchers.IO) {
        scoutDao.clearAllTransientSpots()
    }

    suspend fun toggleSave(id: String, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        scoutDao.setSavedStatus(id, !currentlySaved)
    }

    suspend fun toggleDiscovered(id: String, currentlyDiscovered: Boolean) = withContext(Dispatchers.IO) {
        val newDiscovered = !currentlyDiscovered
        val timestamp = if (newDiscovered) System.currentTimeMillis() else 0L
        scoutDao.setDiscoveredStatus(id, newDiscovered, timestamp)
        achievementEvaluator?.evaluateAllAchievements()
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
        locationName: String = "Local Spot",
        latitude: Double = DefaultLocationConfig.DEFAULT_LAT,
        longitude: Double = DefaultLocationConfig.DEFAULT_LNG
    ): ScoutSpotEntity = withContext(Dispatchers.IO) {
        val newId = "draft_${UUID.randomUUID()}"
        val newSpot = ScoutSpotEntity(
            id = newId,
            title = name,
            description = if (notes.isNotBlank()) notes else "Community discovered secret spot with offline GPS coordinates tagged.",
            category = category.lowercase(),
            distance = "10m radius",
            walkMinutes = 1,
            rating = null,
            reviewCount = null,
            imageUrl = photoUri ?: "ic_cat_${category.lowercase()}",
            badgeLabel = "COMMUNITY GEM",
            statusBadge = "Saved Offline",
            tagsRaw = "Offline Queued|||GPS Cached: ±3m|||Photo Stored Locally",
            proTip = "Saved in local Room database. Auto-sync will transmit when reconnected.",
            contributor = "@you (Offline)",
            upvotes = null,
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

        // Award the photo-verification Scout Points bonus. The award is keyed on the
        // draft id, so a retry or replay of the same submission collides on the ledger's
        // primary key and is ignored — the bonus is never awarded twice for one spot.
        if (!photoUri.isNullOrBlank()) {
            userPointsDao?.awardPoints(
                UserPointsEntity(
                    awardId = "photo_verified_$newId",
                    points = POINTS_PER_PHOTO_VERIFIED_SPOT,
                    reason = "photo_verified_spot",
                    referenceId = newId,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

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

        achievementEvaluator?.evaluateAllAchievements()
        newSpot
    }

    suspend fun deleteSpot(spot: ScoutSpotEntity) = withContext(Dispatchers.IO) {
        scoutDao.deleteSpot(spot)
        discoveredGemDao?.deleteGemById(spot.id)
    }

    /**
     * Syncs a single offline draft to the cloud.
     *
     * Success and failure handling is owned entirely by
     * FirestoreSyncManager.syncDraftSpotToCloud (via [uploadDraftToCloud]): it returns
     * true only after BOTH the public submission and the private note reached Firestore,
     * and it marks the local spot ("Synced to Cloud") and its discovered gem as synced.
     * A false outcome — auth failure, Firestore failure, or a lost private note — means
     * the cloud does not have this draft, so the local row is left exactly as it was:
     * isOfflineDraft stays true, the status badge never claims a successful sync, the
     * discovered gem stays unsynced, and the draft remains in the retry queue of
     * [syncAllDraftsToCloud]. A missing application context is treated the same way:
     * nothing is falsely reported as synced.
     *
     * Retries are safe: the cloud writes are keyed on the spot id with set-merge, so
     * repeated attempts update the same documents instead of creating duplicates.
     *
     * Returns the cloud upload outcome (false when no upload was possible) so callers
     * can distinguish retryable failure from success.
     */
    suspend fun syncDraft(spot: ScoutSpotEntity): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context
        if (appCtx == null) {
            SafeLog.w(TAG, "Cannot sync draft '${spot.id}': application context unavailable; keeping it queued for retry")
            return@withContext false
        }
        uploadDraftToCloud(appCtx, spot)
    }

    /**
     * Delegation point for [syncDraft]. Protected and open so deterministic tests can
     * substitute the cloud outcome without a Firebase connection. The real
     * FirestoreSyncManager owns both halves of the contract: on true it has already
     * updated the local spot ("Synced to Cloud", isOfflineDraft = false) and marked the
     * discovered gem synced; on false it has changed nothing.
     */
    protected open suspend fun uploadDraftToCloud(
        context: android.content.Context,
        spot: ScoutSpotEntity
    ): Boolean = com.example.data.sync.FirestoreSyncManager.syncDraftSpotToCloud(
        context, spot, scoutDao, discoveredGemDao
    )

    /**
     * Uploads every queued offline draft through the same [syncDraft] path used for
     * single-draft syncs, and returns how many succeeded. Failed drafts are left
     * untouched in the queue for the next sweep.
     */
    suspend fun syncAllDraftsToCloud(): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for (draft in scoutDao.getOfflineDrafts().first()) {
            if (syncDraft(draft)) {
                successCount++
            }
        }
        successCount
    }

    private val overpassRetrofitClient = com.example.data.network.OverpassRetrofitClient()
    private val wikidataSearchService = com.example.data.network.WikidataSearchService()

    suspend fun fetchAndSaveWikidataGems(
        lat: Double,
        lng: Double,
        category: String = "all",
        radiusKm: Double = 5.0
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val spots = wikidataSearchService.fetchNearbyWikidataSpots(lat, lng, category, radiusKm)
            if (spots.isNotEmpty()) {
                scoutDao.insertSpots(spots)
            }
            Result.success(spots.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    open suspend fun initializeIfEmpty(): Int = withContext(Dispatchers.IO) {
        val legacySeedIds = listOf("spot_1", "spot_2", "spot_3", "spot_4", "spot_5", "spot_6", "spot_7", "spot_8")
        scoutDao.deleteSpotsByIds(legacySeedIds)
    }
}
