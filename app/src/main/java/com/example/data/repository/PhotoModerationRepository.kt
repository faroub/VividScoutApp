package com.example.data.repository

import com.example.util.PhotoProcessor
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class SpotPhotoData(
    val id: String,
    val spotId: String,
    val photoUrl: String,
    val ownerUid: String,
    val reportsCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isHidden: Boolean
        get() = PhotoProcessor.isPhotoHiddenByReports(reportsCount)
}

/**
 * Repository enforcing atomic photo reporting, duplicate report prevention,
 * and 3-report moderation hiding logic.
 */
class PhotoModerationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "PhotoModerationRepo"
    }

    /**
     * Executes atomic photo report creation and counter increment in a single batch write.
     * Prevents duplicate reports from the same user UID.
     */
    suspend fun reportPhoto(
        photoId: String,
        currentUserUid: String? = auth.currentUser?.uid
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (currentUserUid.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required to report photos."))
        }

        try {
            val photoRef = firestore.collection("spot_photos").document(photoId)
            val reportRef = photoRef.collection("reports").document(currentUserUid)

            // Execute atomic transaction for per-user uniqueness and counter increment
            val newReportCount = firestore.runTransaction { transaction ->
                val reportDoc = transaction.get(reportRef)
                if (reportDoc.exists()) {
                    throw IllegalStateException("User $currentUserUid has already reported photo $photoId.")
                }

                val photoDoc = transaction.get(photoRef)
                val currentCount = photoDoc.getLong("reportsCount")?.toInt() ?: 0
                val updatedCount = currentCount + 1

                // 1. Create per-user unique report document
                val reportData = hashMapOf<String, Any>(
                    "reporterUid" to currentUserUid,
                    "timestamp" to System.currentTimeMillis()
                )
                transaction.set(reportRef, reportData)

                // 2. Increment reportsCount on photo document
                transaction.update(photoRef, "reportsCount", FieldValue.increment(1))

                updatedCount
            }.await()

            SafeLog.i(TAG, "Successfully submitted report for photo $photoId. New reportsCount = $newReportCount")
            Result.success(newReportCount)
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to submit photo report: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Checks if a given photo is hidden from the UI (reportsCount >= 3).
     */
    fun isPhotoHidden(reportsCount: Int): Boolean {
        return PhotoProcessor.isPhotoHiddenByReports(reportsCount)
    }
}
