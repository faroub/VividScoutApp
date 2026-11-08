package com.example.data.repository

import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.util.PhotoResolver
import com.example.util.SafeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Photo-enrichment queue extracted from [ScoutGemRepository]: enriches spots having
 * placeholder or generic image URLs by querying live Wikipedia & Wikimedia Commons APIs
 * for 1200px HD resolution metadata and caching the authentic image URLs directly in
 * Room for permanent offline availability. Prioritizes saved/bookmarked spots first,
 * followed by active feed spots.
 *
 * Room remains the persisted source of truth: a spot is "pending" while its imageUrl
 * is still generic, so the queue re-arms from Room after an app restart.
 *  - in-progress: [inProgressPhotoIds] while its network lookup is running
 *  - completed:   [completedPhotoIds] (authentic URL already persisted to Room)
 *  - retryable:   [failedPhotoAttempts] waiting out exponential backoff
 *  - exhausted:   five failed attempts; skipped until a connectivity re-sync
 */
internal class GemPhotoEnrichment(
    private val scoutDao: ScoutDao,
    private val photoResolver: PhotoResolver
) {

    private companion object {
        const val TAG = "ScoutGemRepository"
    }

    data class PhotoAttemptInfo(
        val failedCount: Int = 0,
        val lastAttemptTimestamp: Long = 0L
    )

    internal val inProgressPhotoIds = Collections.synchronizedSet(HashSet<String>())
    internal val completedPhotoIds = Collections.synchronizedSet(HashSet<String>())
    internal val failedPhotoAttempts = Collections.synchronizedMap(HashMap<String, PhotoAttemptInfo>())
    private val enrichmentMutex = Mutex()

    // Deterministic test seams for backoff timing and request pacing; production
    // uses real wall-clock time and the 50 ms inter-request delay.
    internal var currentTimeMs: () -> Long = { System.currentTimeMillis() }
    internal var enrichmentPacingMs: () -> Long = { ScoutGemRepository.ENRICHMENT_DELAY_MS }

    /**
     * True while [spotId] is in exponential backoff after a failed lookup, or has
     * exhausted its retries. Backoff doubles with each failure (1 s × 2^failures).
     */
    internal fun isBackingOff(spotId: String): Boolean {
        val attempt = failedPhotoAttempts[spotId] ?: return false
        val backoffMs = 1000L * (1 shl attempt.failedCount.coerceAtMost(ScoutGemRepository.MAX_PHOTO_ATTEMPTS))
        return attempt.failedCount >= ScoutGemRepository.MAX_PHOTO_ATTEMPTS ||
            (currentTimeMs() - attempt.lastAttemptTimestamp) < backoffMs
    }

    private fun recordFailedAttempt(spotId: String) {
        val currentFailed = failedPhotoAttempts[spotId]?.failedCount ?: 0
        failedPhotoAttempts[spotId] = PhotoAttemptInfo(currentFailed + 1, currentTimeMs())
    }

    /**
     * Selects the next bounded batch of eligible candidates: pending spots (generic
     * imageUrl) that are neither in progress, completed, nor backing off, with saved
     * places prioritized and then by recency.
     */
    private fun nextEnrichmentBatch(candidates: List<ScoutSpotEntity>): List<ScoutSpotEntity> {
        val eligible = candidates.filter { spot ->
            if (!photoResolver.isGenericOrStockPhoto(spot.imageUrl)) {
                // Room is the source of truth: an authentic persisted URL means completed.
                completedPhotoIds.add(spot.id)
                return@filter false
            }
            if (inProgressPhotoIds.contains(spot.id) || completedPhotoIds.contains(spot.id)) {
                return@filter false
            }
            !isBackingOff(spot.id)
        }.sortedWith(
            compareByDescending<ScoutSpotEntity> { it.isSaved }
                .thenByDescending { it.timestamp }
        )
        return eligible.take(ScoutGemRepository.ENRICHMENT_BATCH_SIZE)
    }

    /**
     * Drains the queue in bounded batches until no eligible candidate remains — there is
     * no fixed pass limit, so a backlog of any size is fully processed across one or more
     * drains. Concurrent invocations merge into the running drain via the mutex; each spot
     * is resolved at most once per drain, and coroutine cancellation reliably clears the
     * in-progress marker.
     */
    suspend fun enrichSpotsPhotosInBackground(spots: List<ScoutSpotEntity> = emptyList()) = withContext(Dispatchers.IO) {
        if (!enrichmentMutex.tryLock()) {
            // A drain is already running; this invocation's candidates are merged into it
            // because every drain re-reads the full candidate set from Room between batches.
            return@withContext
        }

        try {
            var processedThisSession = 0
            var candidates: List<ScoutSpotEntity> = if (spots.isNotEmpty()) spots else scoutDao.getAllSpotsList()

            while (processedThisSession < ScoutGemRepository.MAX_ENRICHMENTS_PER_SESSION) {
                val batch = nextEnrichmentBatch(candidates)
                if (batch.isEmpty()) break // Queue drained: no pending, non-backing-off candidates.

                for (spot in batch) {
                    inProgressPhotoIds.add(spot.id)
                    try {
                        val resolved = photoResolver.resolveEnrichedPhoto(
                            title = spot.title,
                            category = spot.category,
                            districtOrLocation = spot.locationName,
                            latitude = spot.latitude,
                            longitude = spot.longitude,
                            spotId = spot.id
                        )

                        val isAuthenticPhoto = resolved.url.isNotBlank() && !photoResolver.isGenericOrStockPhoto(resolved.url)
                        if (isAuthenticPhoto) {
                            scoutDao.updateImageUrl(spot.id, resolved.url)
                            completedPhotoIds.add(spot.id)
                            failedPhotoAttempts.remove(spot.id)
                            SafeLog.i(
                                TAG,
                                "Wikimedia Commons Resolution SUCCESS: Validated photo for '${spot.title}' (isSaved=${spot.isSaved}): ${resolved.url}"
                            )
                        } else {
                            recordFailedAttempt(spot.id)
                        }
                        delay(enrichmentPacingMs())
                    } catch (e: CancellationException) {
                        throw e // Cancellation must propagate; the finally below clears the in-progress marker.
                    } catch (e: Exception) {
                        recordFailedAttempt(spot.id)
                        SafeLog.w(TAG, "Background photo enrichment error for ${spot.id} ('${spot.title}'): ${e.message}")
                    } finally {
                        inProgressPhotoIds.remove(spot.id)
                    }
                    processedThisSession++
                }

                // Continue the drain: re-read Room so spots inserted mid-session join the queue.
                candidates = scoutDao.getAllSpotsList()
            }

            if (processedThisSession >= ScoutGemRepository.MAX_ENRICHMENTS_PER_SESSION) {
                SafeLog.w(
                    TAG,
                    "Photo enrichment session hit the ${ScoutGemRepository.MAX_ENRICHMENTS_PER_SESSION}-item safety cap; " +
                        "remaining candidates are picked up by the next scheduled drain."
                )
            }
        } finally {
            enrichmentMutex.unlock()
        }
    }
}
