package com.example.data.repository

import android.graphics.Bitmap
import com.example.util.PhotoProcessor
import com.example.util.ProcessedPhoto
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

/**
 * Service orchestrating local image EXIF stripping & 1600px resizing via PhotoProcessor,
 * uploading to ownership-aware Firebase Storage paths (spot_photos/{uid}/{spotId}/{filename}),
 * and persisting consistent Firestore photo metadata.
 */
class FirebasePhotoStorageService(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "FirebasePhotoStorage"
    }

    /**
     * Processes input image stream (resizing to max 1600px, stripping EXIF tags),
     * uploads bytes to ownership-aware path spot_photos/{uid}/{spotId}/{filename},
     * and creates corresponding Firestore photo metadata document.
     */
    suspend fun uploadSpotPhoto(
        spotId: String,
        imageStream: InputStream
    ): Result<SpotPhotoData> = withContext(Dispatchers.IO) {
        val currentUserUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Authentication required to upload spot photo."))

        // 1. Process image stream using PhotoProcessor (resizing to 1600px, stripping EXIF)
        val processResult = PhotoProcessor.processImageStream(imageStream)
        if (processResult.isFailure) {
            val err = processResult.exceptionOrNull()
            SafeLog.w(TAG, "Image processing failed before upload: ${err?.message}")
            return@withContext Result.failure(err ?: IllegalArgumentException("Invalid image file."))
        }

        val processedPhoto = processResult.getOrThrow()
        uploadProcessedPhoto(spotId, currentUserUid, processedPhoto)
    }

    /**
     * Processes bitmap (resizing to max 1600px, stripping EXIF tags),
     * uploads bytes to ownership-aware path spot_photos/{uid}/{spotId}/{filename},
     * and creates corresponding Firestore photo metadata document.
     */
    suspend fun uploadSpotPhotoBitmap(
        spotId: String,
        bitmap: Bitmap
    ): Result<SpotPhotoData> = withContext(Dispatchers.IO) {
        val currentUserUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Authentication required to upload spot photo."))

        // 1. Process bitmap using PhotoProcessor (resizing to 1600px, stripping EXIF)
        val processResult = PhotoProcessor.processBitmap(bitmap)
        if (processResult.isFailure) {
            val err = processResult.exceptionOrNull()
            SafeLog.w(TAG, "Bitmap processing failed before upload: ${err?.message}")
            return@withContext Result.failure(err ?: IllegalArgumentException("Invalid bitmap."))
        }

        val processedPhoto = processResult.getOrThrow()
        uploadProcessedPhoto(spotId, currentUserUid, processedPhoto)
    }

    private suspend fun uploadProcessedPhoto(
        spotId: String,
        userUid: String,
        photo: ProcessedPhoto
    ): Result<SpotPhotoData> = withContext(Dispatchers.IO) {
        try {
            val filename = "${UUID.randomUUID()}.jpg"
            // Ownership-aware Storage path: spot_photos/{uid}/{spotId}/{filename}
            val storageRef = storage.reference.child("spot_photos").child(userUid).child(spotId).child(filename)

            val metadata = StorageMetadata.Builder()
                .setContentType(photo.mimeType)
                .setCustomMetadata("ownerUid", userUid)
                .setCustomMetadata("spotId", spotId)
                .build()

            // 2. Upload processed JPEG bytes
            storageRef.putBytes(photo.jpegBytes, metadata).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 3. Create consistent Firestore photo document
            val photoId = "photo_${UUID.randomUUID()}"
            val photoData = hashMapOf<String, Any>(
                "id" to photoId,
                "spotId" to spotId,
                "photoUrl" to downloadUrl,
                "ownerUid" to userUid,
                "reportsCount" to 0,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("spot_photos").document(photoId).set(photoData).await()

            val resultData = SpotPhotoData(
                id = photoId,
                spotId = spotId,
                photoUrl = downloadUrl,
                ownerUid = userUid,
                reportsCount = 0,
                timestamp = System.currentTimeMillis()
            )

            SafeLog.i(TAG, "Successfully uploaded photo to spot_photos/$userUid/$spotId/$filename")
            Result.success(resultData)
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to upload photo to Storage: ${e.message}")
            Result.failure(e)
        }
    }
}
