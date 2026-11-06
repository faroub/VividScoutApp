package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.util.PhotoProcessor
import com.example.util.ProcessedPhoto
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
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
 * and registering the Firestore photo metadata through the trusted finalizePhotoUpload
 * Cloud Function. The Firestore document is never written directly by the client: the
 * trusted backend verifies the uploaded Storage object (existence, ownership path, size,
 * image type) and generates the photo document id and download URL reference.
 */
class FirebasePhotoStorageService(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "FirebasePhotoStorage"
    }

    /**
     * Processes an image selected via the Android Photo Picker (identified by its content
     * [uri]); the content resolver stream is processed (resized to 1600px, EXIF stripped,
     * decompression-bomb protections in [PhotoProcessor] preserved) and uploaded under the
     * ownership-aware Storage path, with Firestore metadata persisted atomically in
     * [uploadSpotPhoto]. Photo-picker cancellation produces a null [uri] and must be
     * handled by the caller without reporting any result.
     */
    suspend fun uploadSpotPhotoFromUri(
        spotId: String,
        context: Context,
        uri: Uri
    ): Result<SpotPhotoData> = withContext(Dispatchers.IO) {
        runCatching {
            val imageStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Selected photo could not be opened.")
                )
            imageStream.use { stream ->
                uploadSpotPhoto(spotId, stream).getOrThrow()
            }
        }
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

            // 1. Upload processed JPEG bytes (Storage rules enforce path ownership,
            //    size, and image content type at the upload boundary).
            storageRef.putBytes(photo.jpegBytes, metadata).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 2. Register the photo through the trusted finalizePhotoUpload Cloud
            //    Function. It verifies the object exists under the caller's own path,
            //    is a permitted image, and only then creates the Firestore metadata
            //    with a server-generated document id. The client never writes
            //    spot_photos directly (firestore.rules denies client creation).
            val payload = mapOf<String, Any>(
                "spotId" to spotId,
                "filename" to filename,
                "photoUrl" to downloadUrl
            )
            val functions = FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("finalizePhotoUpload").call(payload).await()

            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<*, *>
            val registeredPhotoId = response?.get("photoId") as? String
            val registeredPhotoUrl = response?.get("photoUrl") as? String

            if (registeredPhotoId.isNullOrBlank() || registeredPhotoUrl.isNullOrBlank()) {
                Result.failure(
                    IllegalStateException("Photo registration completed but returned no photo id or URL.")
                )
            } else {
                Result.success(
                    SpotPhotoData(
                        id = registeredPhotoId,
                        spotId = spotId,
                        photoUrl = registeredPhotoUrl,
                        ownerUid = userUid,
                        reportsCount = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to upload photo: ${e.message}")
            Result.failure(e)
        }
    }
}
