package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.TipVoteDao
import com.example.data.local.TipVoteEntity
import com.example.data.model.ScoutCategory
import com.example.util.SafeLog
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            } ?: return null

            val databaseId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                ""
            }

            if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(app, databaseId)
            } else {
                FirebaseFirestore.getInstance(app)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to initialize Firestore instance: ${e.message}")
            null
        }
    }

    private suspend fun ensureFirebaseAuth(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            } ?: return@withContext false

            val auth = com.google.firebase.auth.FirebaseAuth.getInstance(app)
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                SafeLog.d(TAG, "Successfully authenticated Firebase user anonymously (${SafeLog.sanitizeUid(auth.currentUser?.uid)})")
            }
            auth.currentUser != null
        } catch (e: Exception) {
            Log.d(TAG, "Firebase Auth sign-in skipped: ${e.message}")
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
        if (!ensureFirebaseAuth(context)) {
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
     * Idempotent: On retries, preserves existing cloud upvotes and legacyUpvotes fields.
     */
    suspend fun postCommunityTipToCloud(
        context: Context,
        tip: CommunityTipEntity,
        tipDao: CommunityTipDao? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ensureFirebaseAuth(context)) {
            Log.w(TAG, "Cannot post tip '${tip.id}': Firebase Auth required for Firestore writes")
            return@withContext false
        }
        val firestore = getFirestore(context) ?: return@withContext false
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        try {
            val tipRef = firestore.collection(TIPS_COLLECTION).document(tip.id)
            val docSnap = tipRef.get().await()

            if (docSnap.exists()) {
                val originalOwnerUid = docSnap.getString("ownerUid") ?: currentUid
                // Idempotent Retry: Preserve original ownerUid, existing cloud upvotes, and legacyUpvotes!
                val updateData = hashMapOf<String, Any>(
                    "id" to tip.id,
                    "ownerUid" to originalOwnerUid,
                    "spotId" to tip.spotId,
                    "spotTitle" to tip.spotTitle,
                    "authorName" to tip.authorName,
                    "content" to tip.content,
                    "timestamp" to tip.timestamp,
                    "isStatusUpdate" to tip.isStatusUpdate,
                    "statusCategory" to tip.statusCategory
                )
                tipRef.set(updateData, SetOptions.merge()).await()
            } else {
                val createData = hashMapOf<String, Any>(
                    "id" to tip.id,
                    "ownerUid" to currentUid,
                    "spotId" to tip.spotId,
                    "spotTitle" to tip.spotTitle,
                    "authorName" to tip.authorName,
                    "content" to tip.content,
                    "timestamp" to tip.timestamp,
                    "isStatusUpdate" to tip.isStatusUpdate,
                    "statusCategory" to tip.statusCategory,
                    "legacyUpvotes" to tip.legacyUpvotes,
                    "upvotes" to 0
                )
                tipRef.set(createData).await()
            }

            tipDao?.markTipAsSynced(tip.id)
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
        if (!ensureFirebaseAuth(context)) return@withContext false
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
                val tipSnap = transaction.get(tipRef)
                if (!tipSnap.exists() || !tipSnap.contains("legacyUpvotes")) {
                    Log.w(TAG, "Cannot upvote tip '$tipId': Document is missing 'legacyUpvotes' field. Aborting transaction.")
                    return@runTransaction false
                }

                val voteSnap = transaction.get(voteRef)
                if (voteSnap.exists()) {
                    Log.w(TAG, "User '${SafeLog.sanitizeUid(currentUid)}' has already voted on tip '$tipId'. Rejecting second vote.")
                    return@runTransaction false
                }

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
                Log.i(TAG, "Successfully recorded vote from user '${SafeLog.sanitizeUid(currentUid)}' for tip '$tipId'")
            }
            voteSuccess
        } catch (e: Exception) {
            Log.w(TAG, "Failed to execute upvote transaction for tip '$tipId': ${e.message}")
            false
        }
    }

    /**
     * Reconciles user vote state from Cloud Firestore when an upvote transaction encounters an ambiguous outcome or duplicate vote error.
     * If user vote doc exists in Cloud Firestore, ensures local Room DB records the vote.
     */
    suspend fun reconcileTipVoteInCloud(
        context: Context,
        tipId: String,
        tipDao: CommunityTipDao,
        tipVoteDao: TipVoteDao?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ensureFirebaseAuth(context)) return@withContext false
        val firestore = getFirestore(context) ?: return@withContext false
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false

        try {
            val voteRef = firestore.collection(TIPS_COLLECTION).document(tipId).collection("votes").document(currentUid)
            val voteSnap = voteRef.get().await()

            if (voteSnap.exists()) {
                if (tipVoteDao != null && !tipVoteDao.hasUserVoted(tipId, currentUid)) {
                    tipVoteDao.recordVoteAndIncrementCount(tipId, currentUid, tipDao)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reconciliation check failed for tip '$tipId': ${e.message}")
        }
        false
    }

    /**
     * Retries uploading all pending locally authored community tips to Firestore.
     */
    suspend fun syncPendingTipsToCloud(
        context: Context,
        tipDao: CommunityTipDao
    ): Int = withContext(Dispatchers.IO) {
        val pendingTips = tipDao.getLocallyAuthoredTipsSync().filter { !it.isSyncedToCloud }
        var successCount = 0
        for (tip in pendingTips) {
            if (postCommunityTipToCloud(context, tip, tipDao)) {
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
            // 1. Fetch remote community tips using cursor-based pagination (page size = 50, max 500)
            var lastDoc: DocumentSnapshot? = null
            var hasMorePages = true
            val remoteTips = mutableListOf<CommunityTipEntity>()
            var totalFetched = 0

            while (hasMorePages && totalFetched < 500) {
                var query = firestore.collection(TIPS_COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)

                if (lastDoc != null) {
                    query = query.startAfter(lastDoc)
                }

                val pageSnapshot = query.get().await()
                if (pageSnapshot.isEmpty) {
                    hasMorePages = false
                    break
                }

                for (doc in pageSnapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val spotId = doc.getString("spotId") ?: ""
                    val rawContent = doc.getString("content") ?: ""
                    if (spotId.isBlank() || rawContent.isBlank()) continue

                    val content = rawContent.trim().take(500)
                    val author = (doc.getString("authorName") ?: "@scout").trim().take(50)

                    val hasLegacyUpvotesField = doc.contains("legacyUpvotes")
                    val legacyVotes: Int
                    val recordedVotes: Int
                    val isMigrated: Boolean

                    if (hasLegacyUpvotesField) {
                        legacyVotes = (doc.getLong("legacyUpvotes") ?: 0L).toInt()
                        recordedVotes = (doc.getLong("upvotes") ?: 0L).toInt()
                        isMigrated = true
                    } else {
                        val rawUpvotes = (doc.getLong("upvotes") ?: 0L).toInt()
                        legacyVotes = rawUpvotes
                        recordedVotes = 0
                        isMigrated = false
                    }

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
                            legacyUpvotes = legacyVotes,
                            upvotes = recordedVotes,
                            peerNodeId = "cloud-firestore",
                            isLocallyAuthored = false,
                            isSyncedToCloud = true,
                            isMigrated = isMigrated
                        )
                    )
                }

                totalFetched += pageSnapshot.size()
                if (pageSnapshot.size() < 50) {
                    hasMorePages = false
                } else {
                    lastDoc = pageSnapshot.documents.last()
                }
            }

            if (remoteTips.isNotEmpty()) {
                tipDao.insertTips(remoteTips)
                newTipsCount = remoteTips.size
            }

            // 2. Fetch remote community submissions using cursor-based pagination (page size = 30, max 300)
            var lastSubDoc: DocumentSnapshot? = null
            var hasMoreSubPages = true
            var totalSubsFetched = 0
            val existingDraftIds = scoutDao.getAllSpotsSync().filter { it.isOfflineDraft }.map { it.id }.toSet()
            val existingAllIds = scoutDao.getAllSpotsSync().map { it.id }.toSet()
            val remoteSpots = mutableListOf<ScoutSpotEntity>()

            while (hasMoreSubPages && totalSubsFetched < 300) {
                var subQuery = firestore.collection(SUBMISSIONS_COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(30)

                if (lastSubDoc != null) {
                    subQuery = subQuery.startAfter(lastSubDoc)
                }

                val subPageSnapshot = subQuery.get().await()
                if (subPageSnapshot.isEmpty) {
                    hasMoreSubPages = false
                    break
                }

                for (doc in subPageSnapshot.documents) {
                    val id = doc.getString("id") ?: doc.id

                    // Do NOT overwrite locally edited or unsynced drafts!
                    if (id in existingDraftIds) continue
                    if (id in existingAllIds) continue

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

                totalSubsFetched += subPageSnapshot.size()
                if (subPageSnapshot.size() < 30) {
                    hasMoreSubPages = false
                } else {
                    lastSubDoc = subPageSnapshot.documents.last()
                }
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
