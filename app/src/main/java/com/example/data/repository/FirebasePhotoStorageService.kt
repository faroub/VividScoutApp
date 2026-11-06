package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.util.PhotoProcessor
import com.example.util.ProcessedPhoto
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

/**
 * The registered result of the trusted [finalizePhotoUpload] backend verification.
 */
data class FinalizedPhoto(val photoId: String, val photoUrl: String)

/**
 * Thrown when the trusted backend rejects or fails a photo registration.
 * [transient] marks failures a bounded retry can fix (network errors, momentary
 * backend unavailability); definitive rejection (invalid payload, missing object,
 * exhausted rate limit) fails immediately and is never retried.
 */
class PhotoFinalizationException(val transient: Boolean, message: String) : Exception(message)

/**
 * Storage and backend operations used by [FirebasePhotoStorageService]. The production
 * implementation wraps Firebase Storage and the finalizePhotoUpload callable; tests
 * inject fakes to exercise retry, idempotency, and orphan-cleanup behavior.
 */
interface PhotoUploadOperations {
    /** Stores [bytes] under the ownership-aware [path] with the given content type. */
    suspend fun putObject(path: String, bytes: ByteArray, contentType: String, ownerUid: String, spotId: String)

    /** Returns the download URL for the object stored under [path]. */
    suspend fun getDownloadUrl(path: String): String

    /** Deletes the object under [path]; a missing object must not throw. */
    suspend fun deleteObject(path: String)

    /**
     * Registers the uploaded object server-side. Retrying with the same arguments is
     * safe: the backend replays the already-registered photo instead of duplicating it.
     * Throws [PhotoFinalizationException], including its [transient] classification.
     */
    suspend fun finalizeUpload(spotId: String, filename: String, photoUrl: String): FinalizedPhoto
}

/** Production [PhotoUploadOperations] backed by Firebase Storage and Cloud Functions. */
class FirebasePhotoUploadOperations(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : PhotoUploadOperations {

    override suspend fun putObject(path: String, bytes: ByteArray, contentType: String, ownerUid: String, spotId: String) {
        val storageRef = storage.reference.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .setCustomMetadata("ownerUid", ownerUid)
            .setCustomMetadata("spotId", spotId)
            .build()
        storageRef.putBytes(bytes, metadata).await()
    }

    override suspend fun getDownloadUrl(path: String): String =
        storage.reference.child(path).downloadUrl.await().toString()

    override suspend fun deleteObject(path: String) {
        storage.reference.child(path).delete().await()
    }

    override suspend fun finalizeUpload(spotId: String, filename: String, photoUrl: String): FinalizedPhoto {
        val payload = mapOf<String, Any>(
            "spotId" to spotId,
            "filename" to filename,
            "photoUrl" to photoUrl
        )
        try {
            val result = functions.getHttpsCallable("finalizePhotoUpload").call(payload).await()

            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<*, *>
            val photoId = response?.get("photoId") as? String
            val registeredUrl = response?.get("photoUrl") as? String

            if (photoId.isNullOrBlank() || registeredUrl.isNullOrBlank()) {
                throw PhotoFinalizationException(
                    transient = false,
                    message = "Photo registration completed but returned no photo id or URL."
                )
            }
            return FinalizedPhoto(photoId, registeredUrl)
        } catch (e: FirebaseFunctionsException) {
            // Definitive rejections never become valid through repetition.
            val transient = when (e.code) {
                FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                FirebaseFunctionsException.Code.UNAUTHENTICATED,
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> false
                else -> true
            }
            throw PhotoFinalizationException(transient, e.message ?: "Photo registration failed.")
        }
    }
}

/**
 * Owns the complete upload → finalize → orphan-cleanup flow with no Firebase SDK
 * dependency of its own: it works against the [PhotoUploadOperations] seam, which the
 * production implementation backs with Firebase Storage and the finalizePhotoUpload
 * Cloud Function.
 *
 * Orphan control: when the upload succeeds but registration ultimately fails (or the
 * coroutine is cancelled in between), the uploaded object is deleted again so failed
 * uploads never accumulate Storage objects without a matching photo record.
 * Finalization failures classified transient are retried with backoff — always safe
 * because the backend replays an already-registered object instead of creating a
 * duplicate.
 */
class PhotoUploadOrchestrator(private val operations: PhotoUploadOperations) {

    companion object {
        private const val TAG = "PhotoUploadOrchestrator"
        private const val FINALIZE_MAX_ATTEMPTS = 3
        private const val FINALIZE_BACKOFF_MS = 500L
    }

    suspend fun uploadPhoto(
        spotId: String,
        userUid: String,
        photo: ProcessedPhoto,
        nowMs: () -> Long = { System.currentTimeMillis() }
    ): Result<SpotPhotoData> {
        val filename = "${UUID.randomUUID()}.jpg"
        // Ownership-aware Storage path: spot_photos/{uid}/{spotId}/{filename}
        val objectPath = "spot_photos/$userUid/$spotId/$filename"
        var objectUploaded = false

        return try {
            // 1. Upload processed JPEG bytes (Storage rules enforce path ownership,
            //    size, and image content type at the upload boundary).
            operations.putObject(objectPath, photo.jpegBytes, photo.mimeType, userUid, spotId)
            objectUploaded = true
            val downloadUrl = operations.getDownloadUrl(objectPath)

            // 2. Register the photo through the trusted finalizePhotoUpload Cloud
            //    Function. It verifies the object exists under the caller's own path,
            //    is a permitted image, and only then creates the Firestore metadata
            //    with a server-generated document id. The client never writes
            //    spot_photos directly (firestore.rules denies client creation).
            //    Transient failures are retried with the same path — the backend's
            //    idempotent replay guarantees no duplicate photo documents.
            val finalized = finalizeWithRetry {
                operations.finalizeUpload(spotId, filename, downloadUrl)
            }

            Result.success(
                SpotPhotoData(
                    id = finalized.photoId,
                    spotId = spotId,
                    photoUrl = finalized.photoUrl,
                    ownerUid = userUid,
                    reportsCount = 0,
                    timestamp = nowMs()
                )
            )
        } catch (cancellation: CancellationException) {
            // Cancellation must never report success; clean up the object so the
            // interrupted upload does not leave an orphan behind, then propagate.
            if (objectUploaded) deleteOrphanedObject(objectPath)
            throw cancellation
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to upload photo: ${e.message}")
            if (objectUploaded) deleteOrphanedObject(objectPath)
            Result.failure(e)
        }
    }

    /**
     * Bounded retry for the trusted registration. Only failures the backend could not
     * have processed (transient network/unavailability) are retried; definitive
     * [PhotoFinalizationException]s fail immediately.
     */
    private suspend fun finalizeWithRetry(finalize: suspend () -> FinalizedPhoto): FinalizedPhoto {
        var lastError: Throwable? = null
        repeat(FINALIZE_MAX_ATTEMPTS) { attempt ->
            try {
                return finalize()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: PhotoFinalizationException) {
                if (!e.transient) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
            if (attempt < FINALIZE_MAX_ATTEMPTS - 1) delay(FINALIZE_BACKOFF_MS)
        }
        throw lastError ?: IllegalStateException("Photo registration failed without a specific error.")
    }

    /**
     * Best-effort removal of an object whose registration never completed. Failure here
     * is logged (without exposing private user information) and never masks the original
     * upload error: the hourly reapOrphanedPhotoUploads Cloud Function still deletes the
     * object once it is older than the reaper's safety window.
     */
    private suspend fun deleteOrphanedObject(objectPath: String) {
        try {
            operations.deleteObject(objectPath)
        } catch (cleanupError: Exception) {
            if (cleanupError is CancellationException) throw cleanupError
            SafeLog.w(TAG, "Failed to delete orphaned photo object: ${cleanupError.message}")
        }
    }
}

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
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val operations: PhotoUploadOperations = FirebasePhotoUploadOperations(storage)
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
        PhotoUploadOrchestrator(operations).uploadPhoto(spotId, userUid, photo)
    }
}
