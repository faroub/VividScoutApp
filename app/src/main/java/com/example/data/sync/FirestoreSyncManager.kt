package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutCategory
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
            configureAppCheck(context)
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

    private suspend fun ensureFirebaseAuth(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Log.d(TAG, "Successfully authenticated Firebase user anonymously (${auth.currentUser?.uid})")
            }
            auth.currentUser != null
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Auth anonymous sign-in failed: ${e.message}")
            false
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
        if (!ensureFirebaseAuth()) {
            Log.w(TAG, "Cannot sync draft '${spot.id}': Firebase Auth required for Firestore writes")
            return@withContext false
        }
        val firestore = getFirestore(context) ?: return@withContext false
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        try {
            val docData = hashMapOf(
                "id" to spot.id,
                "ownerUid" to currentUid,
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
        if (!ensureFirebaseAuth()) {
            Log.w(TAG, "Cannot post tip '${tip.id}': Firebase Auth required for Firestore writes")
            return@withContext false
        }
        val firestore = getFirestore(context) ?: return@withContext false
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        try {
            val tipData = hashMapOf(
                "id" to tip.id,
                "ownerUid" to currentUid,
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
     * Synchronizes a +1 upvote for a community tip using a Firestore transaction.
     * Checks if the vote document at community_tips/{tipId}/votes/{currentUid} already exists,
     * rejecting second votes from the same user, and writing the vote doc together with the upvote increment.
     */
    suspend fun incrementTipUpvoteInCloud(
        context: Context,
        tipId: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ensureFirebaseAuth()) return@withContext false
        val firestore = getFirestore(context) ?: return@withContext false
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            Log.w(TAG, "Cannot upvote tip '$tipId': No authenticated user UID found")
            return@withContext false
        }

        try {
            val tipRef = firestore.collection(TIPS_COLLECTION).document(tipId)
            val voteRef = tipRef.collection("votes").document(currentUid)

            val voteSuccess = firestore.runTransaction { transaction ->
                val voteSnap = transaction.get(voteRef)
                if (voteSnap.exists()) {
                    Log.w(TAG, "User '$currentUid' has already voted on tip '$tipId'. Rejecting second vote.")
                    return@runTransaction false
                }

                val tipSnap = transaction.get(tipRef)
                val currentUpvotes = tipSnap.getLong("upvotes") ?: 0L

                val voteData = hashMapOf<String, Any>(
                    "uid" to currentUid,
                    "timestamp" to System.currentTimeMillis()
                )

                // Set vote doc in votes subcollection AND increment tip upvotes by 1
                transaction.set(voteRef, voteData)
                transaction.update(tipRef, "upvotes", currentUpvotes + 1)
                true
            }.await()

            if (voteSuccess) {
                Log.i(TAG, "Successfully recorded vote from user '$currentUid' for tip '$tipId'")
            }
            voteSuccess
        } catch (e: Exception) {
            Log.w(TAG, "Failed to execute upvote transaction for tip '$tipId': ${e.message}")
            false
        }
    }

    /**
     * Retries uploading all pending locally authored community tips to Firestore.
     */
    suspend fun syncPendingTipsToCloud(
        context: Context,
        tipDao: CommunityTipDao
    ): Int = withContext(Dispatchers.IO) {
        val pendingTips = tipDao.getLocallyAuthoredTipsSync()
        var successCount = 0
        for (tip in pendingTips) {
            if (postCommunityTipToCloud(context, tip)) {
                successCount++
            }
        }
        successCount
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
                val rawContent = doc.getString("content") ?: ""
                if (spotId.isBlank() || rawContent.isBlank()) continue

                val content = rawContent.trim().take(500)
                val author = (doc.getString("authorName") ?: "@scout").trim().take(50)

                remoteTips.add(
                    CommunityTipEntity(
                        id = id.take(128),
                        spotId = spotId.take(128),
                        spotTitle = (doc.getString("spotTitle") ?: "Community Gem").trim().take(120),
                        authorName = author,
                        content = content,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        isStatusUpdate = doc.getBoolean("isStatusUpdate") ?: false,
                        statusCategory = (doc.getString("statusCategory") ?: "INSIGHT").trim().take(32),
                        upvotes = (doc.getLong("upvotes") ?: 1L).toInt().coerceAtLeast(1),
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

                val rawTitle = doc.getString("title") ?: continue
                val lat = doc.getDouble("latitude") ?: continue
                val lng = doc.getDouble("longitude") ?: continue

                // Validate coordinates are geographically plausible
                if (lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0) || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                    Log.w(TAG, "Skipping remote submission '$id': invalid coordinates ($lat, $lng)")
                    continue
                }

                val title = rawTitle.trim().take(120)
                if (title.length < 2) continue

                val rawCategory = doc.getString("category") ?: "sights"
                val normalizedCategory = ScoutCategory.normalize(rawCategory)

                val description = (doc.getString("description") ?: "Shared community scout gem.").trim().take(1000)
                val locationName = (doc.getString("locationName") ?: "Community Location").trim().take(100)
                val contributor = (doc.getString("contributor") ?: "@CommunityScout").trim().take(50)
                val userNotes = (doc.getString("userNotes") ?: "Shared by community scout.").trim().take(500)

                remoteSpots.add(
                    ScoutSpotEntity(
                        id = id.take(128),
                        title = title,
                        description = description,
                        category = normalizedCategory,
                        distance = "Discovered",
                        walkMinutes = 5,
                        rating = null,
                        reviewCount = null,
                        imageUrl = doc.getString("photoUri") ?: "ic_cat_sights",
                        badgeLabel = "COMMUNITY SCOUT",
                        statusBadge = "Community Shared",
                        tagsRaw = "Community Shared|||Cloud Sync",
                        proTip = userNotes,
                        contributor = contributor,
                        upvotes = 1,
                        isSaved = false,
                        isOfflineDraft = false,
                        isDiscovered = false,
                        offlineCached = true,
                        locationName = locationName,
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
