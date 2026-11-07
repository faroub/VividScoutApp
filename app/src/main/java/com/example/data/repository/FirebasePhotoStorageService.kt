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
import java.security.MessageDigest

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
 * Signals a photo-upload failure together with the one fact callers must not lose:
 * whether the trusted registration call had already been attempted when the flow
 * failed. When it was, the outcome is ambiguous — the registration may have committed
 * on the server while its response was lost on the network — so a retry must reconcile
 * with the server instead of assuming nothing was published. The orchestrator's
 * content-derived Storage path makes that automatic: retrying the same photo reuses
 * the exact registration identity and replays the already-published photo.
 */
class PhotoUploadException(
    message: String,
    cause: Throwable? = null,
    val registrationAttempted: Boolean
) : Exception(message, cause)

/**
 * Storage and backend operations used by [FirebasePhotoStorageService]. The production
 * implementation wraps Firebase Storage and the finalizePhotoUpload callable; tests
 * inject fakes to exercise retry, idempotency, and ambiguity behavior.
 */
interface PhotoUploadOperations {
    /** Stores [bytes] under the ownership-aware [path] with the given content type. */
    suspend fun putObject(path: String, bytes: ByteArray, contentType: String, ownerUid: String, spotId: String)

    /** Returns the download URL for the object stored under [path]. */
    suspend fun getDownloadUrl(path: String): String

    /**
     * Reports whether an object already exists under [path]. Returns false on any error
     * (a failed check safely falls back to attempting the upload; Storage rules reject
     * an overwrite of an existing object anyway).
     */
    suspend fun objectExists(path: String): Boolean

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

    override suspend fun objectExists(path: String): Boolean =
        try {
            storage.reference.child(path).metadata.await()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Any lookup failure (missing object, network error) safely falls back to
            // attempting the upload; Storage rules reject an overwrite anyway.
            false
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
 * Owns the complete upload → finalize flow with no Firebase SDK dependency of its
 * own: it works against the [PhotoUploadOperations] seam, which the production
 * implementation backs with Firebase Storage and the finalizePhotoUpload Cloud
 * Function.
 *
 * Orphan control and ambiguity: community photo objects are immutable and
 * non-deletable by clients (storage.rules denies update AND delete to everyone), so a
 * failed upload must NOT attempt a client-side Storage deletion. Objects whose
 * registration never completed are removed exclusively by the scheduled backend
 * reaper (reapUnregisteredPhotoUploads) once they are older than its 24 h safety
 * window — the same sweep that guarantees a registered photo is never touched.
 *
 * Ambiguous outcomes ("the server may have registered the photo after the client lost
 * the response") are reconciled rather than guessed: the Storage path is derived from
 * the photo content, so any retry of the same image reuses the exact object path and
 * the backend's deterministic registration id — the finalizePhotoUpload replay returns
 * the already-published photo instead of publishing a duplicate. The same applies when
 * the object is already in place: the upload is skipped and registration reconciles.
 * Finalization failures classified transient are retried with the same identity.
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
        // Content-derived object name: retrying the same image (including after an
        // ambiguous finalization whose response was lost) reuses the exact Storage
        // path — and therefore the exact registration identity.
        val filename = "${contentSha256Hex(photo.jpegBytes)}.jpg"
        // Ownership-aware Storage path: spot_photos/{uid}/{spotId}/{filename}
        val objectPath = "spot_photos/$userUid/$spotId/$filename"
        var registrationAttempted = false

        return try {
            // 1. Upload processed JPEG bytes (Storage rules enforce path ownership,
            //    size, and image content type at the upload boundary). Skip when this
            //    exact photo object is already in place: registration below then
            //    reconciles with whatever the server already published for it.
            if (!operations.objectExists(objectPath)) {
                operations.putObject(objectPath, photo.jpegBytes, photo.mimeType, userUid, spotId)
            }
            val downloadUrl = operations.getDownloadUrl(objectPath)

            // 2. Register the photo through the trusted finalizePhotoUpload Cloud
            //    Function. It verifies the object exists under the caller's own path,
            //    is a permitted image, and only then creates the Firestore metadata
            //    with a deterministic document id. The client never writes
            //    spot_photos directly (firestore.rules denies client creation).
            //    Transient failures are retried with the same identity — the backend's
            //    idempotent replay guarantees no duplicate photo documents.
            registrationAttempted = true
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
            // Cancellation must never report success. The object stays in Storage for
            // the backend reaper to clean up once older than its safety window — the
            // client no longer has (and must not have) any deletion capability.
            SafeLog.w(TAG, "Photo upload cancelled before registration completed; object left for the backend reaper")
            throw cancellation
        } catch (e: Exception) {
            // The uploaded object is left in place intentionally: the registration may
            // have committed on the server while its response was lost. When
            // registration was attempted the failure is therefore ambiguous — the
            // caller must retry (which reconciles through the deterministic path),
            // never assume nothing was published.
            SafeLog.w(TAG, "Failed to upload photo: ${e.message}")
            Result.failure(
                PhotoUploadException(
                    message = if (registrationAttempted) {
                        "Photo upload failed after registration was attempted — the photo may already be published. Retrying reconciles with the server instead of duplicating the photo."
                    } else {
                        e.message ?: "Photo upload failed."
                    },
                    cause = e,
                    registrationAttempted = registrationAttempted
                )
            )
        }
    }

    /** SHA-256 hex digest of the encoded photo bytes: the content identity. */
    private fun contentSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            builder.append("%02x".format(byte))
        }
        return builder.toString()
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
        // Explicit try/catch instead of runCatching: runCatching would also catch
        // CancellationException and convert structured-concurrency cancellation into an
        // ordinary failure. Cancellation must propagate.
        try {
            val imageStream = context.contentResolver.openInputStream(uri)
            if (imageStream == null) {
                Result.failure(IllegalArgumentException("Selected photo could not be opened."))
            } else {
                Result.success(imageStream.use { stream -> uploadSpotPhoto(spotId, stream).getOrThrow() })
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Result.failure(e)
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
