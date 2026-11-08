package com.example.data.sync

import android.content.Context
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutCategory
import com.example.util.SafeLog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Community download-side synchronization extracted from [FirestoreSyncManager]:
 * cursor-paginated fetch of remote tips and community submissions into Room, and
 * restoration of the authenticated user's own private spot notes.
 *
 * Photo handling follows the never-render-an-external-URL contract: submissions
 * store either '' or an owned Storage path, and the download URL is resolved at
 * display time. Legacy documents carrying external URLs are counted and shown
 * without a photo; no legacy data is deleted.
 */
internal object CommunitySyncDownloader {

    /**
     * Downloads recent remote community tips and submissions from Firestore, updating local Room DB.
     */
    suspend fun syncRemoteContentDown(
        context: Context,
        tipDao: CommunityTipDao,
        scoutDao: ScoutDao
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val firestore = FirebaseAccess.getFirestore(context) ?: return@withContext Pair(0, 0)
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
            var externalPhotoUriSkipped = 0

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
                        SafeLog.w(TAG, "Skipping remote submission '$id': invalid coordinates (${SafeLog.sanitizeCoordinate(lat)}, ${SafeLog.sanitizeCoordinate(lng)})")
                        continue
                    }

                    val title = rawTitle.trim().take(120)
                    if (title.length < 2) continue

                    // Photo handling: submissions store either '' or an owned Storage
                    // path. The download URL is resolved at display/materialization time
                    // so the stored document never carries a third-party URL. Legacy
                    // documents with external URLs are NOT rendered (fetching them would
                    // expose every viewer's IP to a third-party host) — they are counted,
                    // logged, and shown without a photo. No legacy data is deleted.
                    val rawPhotoUri = doc.getString("photoUri")
                    val displayImageUrl = when (classifySubmissionPhotoForDisplay(rawPhotoUri)) {
                        SubmissionDisplayPhoto.NoPhoto -> "ic_cat_sights"
                        SubmissionDisplayPhoto.ExternalRejected -> {
                            externalPhotoUriSkipped++
                            "ic_cat_sights"
                        }
                        SubmissionDisplayPhoto.StoragePath -> {
                            try {
                                com.google.firebase.storage.FirebaseStorage.getInstance()
                                    .getReference(rawPhotoUri!!)
                                    .downloadUrl.await().toString()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                SafeLog.w(TAG, "Submission photo '$rawPhotoUri' could not be resolved; showing no photo")
                                "ic_cat_sights"
                            }
                        }
                    }

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
                            imageUrl = displayImageUrl,
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
            if (externalPhotoUriSkipped > 0) {
                SafeLog.w(TAG, "Skipped $externalPhotoUriSkipped community submission photo(s) with non Storage URLs (legacy external URLs are never rendered; no data was deleted)")
            }

            // 3. Restore this user's own private notes from the owner-scoped collection
            // users/{uid}/private_spot_notes. Notes are only ever filled into spots
            // whose local Room note is still blank — local edits always win, so an
            // offline-first user never loses a note written on this device.
            syncOwnPrivateNotesDown(context, scoutDao)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "Error synchronizing remote content from Firestore: ${e.message}", e)
        }

        Pair(newTipsCount, newGemsCount)
    }

    /**
     * Downloads the authenticated user's own private spot notes and fills any local
     * Room note that is still blank. Other users' notes are unreachable: the collection
     * path key is the authenticated UID and the Firestore rules deny every access from
     * anyone else (see users/{uid}/private_spot_notes in firestore.rules).
     */
    private suspend fun syncOwnPrivateNotesDown(
        context: Context,
        scoutDao: ScoutDao
    ): Int = withContext(Dispatchers.IO) {
        if (!FirebaseAccess.ensureFirebaseAuth(context)) return@withContext 0
        val firestore = FirebaseAccess.getFirestore(context) ?: return@withContext 0
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext 0

        var restoredCount = 0
        try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(currentUid)
                .collection(PRIVATE_NOTES_COLLECTION)
                .get()
                .await()

            for (noteDoc in snapshot.documents) {
                val spotId = noteDoc.getString("spotId") ?: continue
                val notes = noteDoc.getString("notes") ?: continue
                if (notes.isBlank()) continue
                val localSpot = scoutDao.getSpotById(spotId) ?: continue
                if (!localSpot.userNotes.isNullOrBlank()) continue
                scoutDao.updateUserNotes(spotId, notes)
                restoredCount++
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to restore private spot notes: ${e.message}")
        }
        restoredCount
    }
}
