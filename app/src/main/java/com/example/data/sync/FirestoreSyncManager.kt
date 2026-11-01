package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "FirestoreSyncManager"
private const val SUBMISSIONS_COLLECTION = "community_submissions"
private const val TIPS_COLLECTION = "community_tips"

/**
 * Manages bidirectional cloud synchronization between local Room entities and Firebase Firestore.
 * Handles uploading offline drafts (isOfflineDraft == true) and user-authored community tips,
 * and fetching community-shared gems and peer tips from the cloud.
 */
object FirestoreSyncManager {

    private fun getFirestore(context: Context): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            val databaseId = if (resId != 0) {
                context.getString(resId)
            } else {
                "ai-studio-android-vividsco-23e1f418-990c-46e5-8dcb-37171a4b89b4"
            }
            if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(FirebaseApp.getInstance(), databaseId)
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Firestore instance: ${e.message}")
            null
        }
    }

    /**
     * Uploads an individual offline draft spot to the Firestore 'community_submissions' collection.
     */
    suspend fun syncDraftSpotToCloud(
        context: Context,
        spot: ScoutSpotEntity,
        scoutDao: ScoutDao,
        discoveredGemDao: DiscoveredGemDao?
    ): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestore(context) ?: return@withContext false
        try {
            val docData = hashMapOf(
                "id" to spot.id,
                "title" to spot.title,
                "description" to spot.description,
                "category" to spot.category,
                "locationName" to spot.locationName,
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "photoUri" to (spot.imageUrl ?: ""),
                "userNotes" to (spot.userNotes ?: ""),
                "contributor" to spot.contributor,
                "timestamp" to spot.timestamp
            )

            firestore.collection(SUBMISSIONS_COLLECTION)
                .document(spot.id)
                .set(docData, SetOptions.merge())
                .await()

            val syncedSpot = spot.copy(
                isOfflineDraft = false,
                statusBadge = "Synced to Cloud"
            )
            scoutDao.updateSpot(syncedSpot)
            discoveredGemDao?.markAsSynced(spot.id)
            Log.i(TAG, "Successfully synced spot '${spot.title}' to cloud Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync draft spot '${spot.id}' to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Synchronizes all pending offline drafts (isOfflineDraft == true) to Firestore.
     */
    suspend fun syncAllOfflineDrafts(
        context: Context,
        scoutDao: ScoutDao,
        discoveredGemDao: DiscoveredGemDao?
    ): Int = withContext(Dispatchers.IO) {
        val drafts = scoutDao.getAllSpotsSync().filter { it.isOfflineDraft }
        var successCount = 0
        for (draft in drafts) {
            if (syncDraftSpotToCloud(context, draft, scoutDao, discoveredGemDao)) {
                successCount++
            }
        }
        successCount
    }

    /**
     * Posts a community tip or status update to the Firestore 'community_tips' collection.
     */
    suspend fun postCommunityTipToCloud(
        context: Context,
        tip: CommunityTipEntity
    ): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestore(context) ?: return@withContext false
        try {
            val tipData = hashMapOf(
                "id" to tip.id,
                "spotId" to tip.spotId,
                "spotTitle" to tip.spotTitle,
                "authorName" to tip.authorName,
                "content" to tip.content,
                "timestamp" to tip.timestamp,
                "isStatusUpdate" to tip.isStatusUpdate,
                "statusCategory" to tip.statusCategory,
                "upvotes" to tip.upvotes
            )

            firestore.collection(TIPS_COLLECTION)
                .document(tip.id)
                .set(tipData, SetOptions.merge())
                .await()

            Log.i(TAG, "Posted community tip '${tip.id}' to cloud Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post community tip to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Downloads recent remote community tips and submissions from Firestore, updating local Room DB.
     */
    suspend fun syncRemoteContentDown(
        context: Context,
        tipDao: CommunityTipDao,
        scoutDao: ScoutDao
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val firestore = getFirestore(context) ?: return@withContext Pair(0, 0)
        var newTipsCount = 0
        var newGemsCount = 0

        try {
            // 1. Fetch remote community tips
            val tipsSnapshot = firestore.collection(TIPS_COLLECTION)
                .limit(50)
                .get()
                .await()

            val remoteTips = mutableListOf<CommunityTipEntity>()
            for (doc in tipsSnapshot.documents) {
                val id = doc.getString("id") ?: doc.id
                val spotId = doc.getString("spotId") ?: ""
                val content = doc.getString("content") ?: ""
                if (spotId.isBlank() || content.isBlank()) continue

                remoteTips.add(
                    CommunityTipEntity(
                        id = id,
                        spotId = spotId,
                        spotTitle = doc.getString("spotTitle") ?: "Community Gem",
                        authorName = doc.getString("authorName") ?: "@scout",
                        content = content,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        isStatusUpdate = doc.getBoolean("isStatusUpdate") ?: false,
                        statusCategory = doc.getString("statusCategory") ?: "INSIGHT",
                        upvotes = (doc.getLong("upvotes") ?: 1L).toInt(),
                        peerNodeId = "cloud-firestore",
                        isLocallyAuthored = false
                    )
                )
            }
            if (remoteTips.isNotEmpty()) {
                tipDao.insertTips(remoteTips)
                newTipsCount = remoteTips.size
            }

            // 2. Fetch remote community submissions
            val subsSnapshot = firestore.collection(SUBMISSIONS_COLLECTION)
                .limit(30)
                .get()
                .await()

            val existingIds = scoutDao.getAllSpotsSync().map { it.id }.toSet()
            val remoteSpots = mutableListOf<ScoutSpotEntity>()
            for (doc in subsSnapshot.documents) {
                val id = doc.getString("id") ?: doc.id
                if (id in existingIds) continue

                val title = doc.getString("title") ?: continue
                val lat = doc.getDouble("latitude") ?: continue
                val lng = doc.getDouble("longitude") ?: continue

                remoteSpots.add(
                    ScoutSpotEntity(
                        id = id,
                        title = title,
                        description = doc.getString("description") ?: "Shared community scout gem.",
                        category = doc.getString("category") ?: "sights",
                        distance = "Discovered",
                        walkMinutes = 5,
                        rating = null,
                        reviewCount = null,
                        imageUrl = doc.getString("photoUri") ?: "ic_cat_sights",
                        badgeLabel = "COMMUNITY SCOUT",
                        statusBadge = "Synced from Cloud",
                        tagsRaw = "Community Verified|||Cloud Sync",
                        proTip = doc.getString("userNotes") ?: "Discovered by community scout.",
                        contributor = doc.getString("contributor") ?: "@CommunityScout",
                        upvotes = 1,
                        isSaved = false,
                        isOfflineDraft = false,
                        isDiscovered = false,
                        offlineCached = true,
                        locationName = doc.getString("locationName") ?: "Community Location",
                        latitude = lat,
                        longitude = lng,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                )
            }
            if (remoteSpots.isNotEmpty()) {
                scoutDao.insertSpots(remoteSpots)
                newGemsCount = remoteSpots.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing remote content from Firestore: ${e.message}", e)
        }

        Pair(newTipsCount, newGemsCount)
    }
}
