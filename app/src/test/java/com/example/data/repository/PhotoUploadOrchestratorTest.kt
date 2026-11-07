package com.example.data.repository

import com.example.util.ProcessedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the client-side behavior of [PhotoUploadOrchestrator] against a fake
 * [PhotoUploadOperations]: because storage.rules make community photo objects
 * immutable and non-deletable by clients, a failed or interrupted registration must
 * NEVER attempt a client-side Storage deletion — the uploaded object is left for the
 * scheduled backend reaper (the only authorized cleanup path). Transient registration
 * failures are retried bounded with the same object path and identity (the backend
 * replays registered objects, so no duplicate is ever created), definitive rejections
 * fail immediately, and cancellation never reports success.
 *
 * Ambiguity handling: when the trusted registration was attempted but its result is
 * unknown (e.g. the server committed the registration and then the response was lost),
 * the failure is reported as ambiguous (PhotoUploadException.registrationAttempted),
 * the object is kept, and a retry reconciles with the server through the deterministic
 * content-derived path — returning the already-published photo instead of a duplicate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoUploadOrchestratorTest {

    private val processedPhoto = ProcessedPhoto(
        jpegBytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte()),
        width = 800,
        height = 600,
        mimeType = "image/jpeg"
    )

    private class FakePhotoUploadOperations : PhotoUploadOperations {
        val uploadedPaths = mutableListOf<String>()
        var putObjectError: Exception? = null
        var downloadUrlError: Exception? = null
        val finalizeCalls = mutableListOf<Triple<String, String, String>>()
        val finalizeOutcomes = ArrayDeque<() -> FinalizedPhoto>()

        override suspend fun putObject(
            path: String,
            bytes: ByteArray,
            contentType: String,
            ownerUid: String,
            spotId: String
        ) {
            putObjectError?.let { throw it }
            uploadedPaths.add(path)
        }

        override suspend fun getDownloadUrl(path: String): String {
            downloadUrlError?.let { throw it }
            return "https://firebasestorage.googleapis.com/v0/b/bucket/o/${path.replace("/", "%2F")}?alt=media&token=test"
        }

        override suspend fun objectExists(path: String): Boolean = path in uploadedPaths

        override suspend fun finalizeUpload(spotId: String, filename: String, photoUrl: String): FinalizedPhoto {
            finalizeCalls.add(Triple(spotId, filename, photoUrl))
            return finalizeOutcomes.removeFirst().invoke()
        }

        /** Enqueues a successful registration returning a server-style photo id. */
        fun enqueueSuccessfulFinalize(photoId: String = "photo-doc-1") {
            finalizeOutcomes.add {
                FinalizedPhoto(
                    photoId,
                    "https://firebasestorage.googleapis.com/v0/b/bucket/o/registered?alt=media&token=ok"
                )
            }
        }

        fun enqueueFailingFinalize(transient: Boolean, message: String = "registration failed") {
            finalizeOutcomes.add { throw PhotoFinalizationException(transient, message) }
        }

        fun enqueueThrowingFinalize(error: Throwable) {
            finalizeOutcomes.add { throw error }
        }

        /**
         * Enqueues the response-loss scenario: the server commits the registration
         * and then the network fails before the client receives the response. The
         * first call throws a network error; any later call replays the registration
         * exactly like the production backend's idempotent replay.
         */
        fun enqueueResponseLoss(photoId: String = "photo-doc-1") {
            var registered = false
            finalizeOutcomes.add {
                registered = true
                throw java.io.IOException("network lost after the server committed the registration")
            }
            finalizeOutcomes.add {
                if (!registered) throw IllegalStateException("expected the registration to have committed")
                FinalizedPhoto(
                    photoId,
                    "https://firebasestorage.googleapis.com/v0/b/bucket/o/registered?alt=media&token=ok"
                )
            }
        }
    }

    @Test
    fun successfulUpload_registersAndNeverDeletesTheObject() = runTest {
        val fake = FakePhotoUploadOperations().apply { enqueueSuccessfulFinalize() }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue("A verified registration must succeed", result.isSuccess)
        assertEquals("photo-doc-1", result.getOrThrow().id)
        assertEquals("uid-1", result.getOrThrow().ownerUid)
        assertEquals("spot-1", result.getOrThrow().spotId)
        assertEquals(0, result.getOrThrow().reportsCount)
        assertEquals(1, fake.uploadedPaths.size)
        assertEquals("spot_photos/uid-1/spot-1/${fake.uploadedPaths.first().substringAfterLast('/')}", fake.uploadedPaths.first())
        assertEquals(1, fake.finalizeCalls.size)
    }

    @Test
    fun transientFinalizeFailure_isRetriedAndThenSucceeds() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = true, "network unavailable")
            enqueueFailingFinalize(transient = true, "network unavailable")
            enqueueSuccessfulFinalize("photo-doc-2")
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue("A transient failure followed by success must succeed overall", result.isSuccess)
        assertEquals("photo-doc-2", result.getOrThrow().id)
        assertEquals("transient failures must be retried", 3, fake.finalizeCalls.size)
    }

    @Test
    fun permanentFinalizeFailure_failsImmediatelyAndLeavesTheObjectToTheBackendReaper() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = false, "invalid photo")
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue("A definitive rejection must be a failure", result.isFailure)
        assertEquals(
            "Definitive rejections must never be retried",
            1,
            fake.finalizeCalls.size
        )
        assertEquals(
            "The object must remain in Storage for the scheduled backend reaper",
            1,
            fake.uploadedPaths.size
        )
    }

    @Test
    fun allFinalizeAttemptsFailing_reportsAmbiguityAndLeavesTheObjectToTheBackendReaper() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = true, "unavailable 1")
            enqueueFailingFinalize(transient = true, "unavailable 2")
            enqueueFailingFinalize(transient = true, "unavailable 3")
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        assertEquals("the bounded retry must stop at 3 attempts", 3, fake.finalizeCalls.size)
        assertEquals(1, fake.uploadedPaths.size)
        // Registration was attempted: the failure is ambiguous (the server may have
        // committed a registration whose response was lost), which callers must see —
        // never a plain "nothing happened" error.
        val exception = result.exceptionOrNull()
        assertTrue("an attempted registration surfaces as PhotoUploadException", exception is PhotoUploadException)
        assertTrue(
            "the error states the photo may already be published",
            (exception as PhotoUploadException).registrationAttempted
        )
    }

    @Test
    fun uploadFailureBeforeRegistration_isUnambiguous() = runTest {
        // A failure before any registration attempt (here: the download URL step) must
        // not be labelled ambiguous — nothing can have been published yet.
        val fake = FakePhotoUploadOperations().apply {
            downloadUrlError = IllegalStateException("download url unavailable")
            enqueueSuccessfulFinalize()
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PhotoUploadException)
        assertEquals(false, (exception as PhotoUploadException).registrationAttempted)
        assertEquals("download url unavailable", exception.message)
    }

    @Test
    fun putObjectFailure_reportsFailureWithoutRegistration() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            putObjectError = IllegalStateException("storage write failed")
            enqueueSuccessfulFinalize()
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        assertEquals("storage write failed", result.exceptionOrNull()?.message)
        assertEquals(
            "Nothing was uploaded, so there must be no object left behind",
            0,
            fake.uploadedPaths.size
        )
        assertEquals("Registration must never run when the upload failed", 0, fake.finalizeCalls.size)
    }

    @Test
    fun downloadUrlFailure_reportsFailureAndLeavesTheObjectToTheBackendReaper() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            downloadUrlError = IllegalStateException("download url unavailable")
            enqueueSuccessfulFinalize()
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        assertEquals("download url unavailable", result.exceptionOrNull()?.message)
        assertEquals(1, fake.uploadedPaths.size)
        assertEquals("Registration must never run without a download URL", 0, fake.finalizeCalls.size)
    }

    @Test
    fun cancellationAfterUpload_propagatesAndLeavesTheObjectToTheBackendReaper() {
        val fake = FakePhotoUploadOperations().apply {
            enqueueThrowingFinalize(CancellationException("scope cancelled"))
        }
        val cancellation = assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
            }
        }
        assertEquals("scope cancelled", cancellation.message)
        assertEquals(
            "An interrupted upload must not delete its object: clients have no deletion capability, the backend reaper cleans up",
            1,
            fake.uploadedPaths.size
        )
    }

    @Test
    fun retryReusesTheSameObjectPath_neverCausingDuplicateRegistrations() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = true, "unavailable 1")
            enqueueSuccessfulFinalize()
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isSuccess)
        // One object only: retries re-finalize the same uploaded path (the backend
        // replays an already-registered object instead of creating a duplicate).
        assertEquals(1, fake.uploadedPaths.size)
        assertEquals(2, fake.finalizeCalls.size)
        val (firstSpotId, firstFilename, firstUrl) = fake.finalizeCalls.first()
        val (retrySpotId, retryFilename, retryUrl) = fake.finalizeCalls.last()
        assertEquals(firstSpotId, retrySpotId)
        assertEquals(firstFilename, retryFilename)
        assertEquals(firstUrl, retryUrl)
    }

    @Test
    fun responseLoss_retriesReconcileWithTheCommittedRegistration() = runTest {
        // The server commits the registration and then the response is lost on the
        // network. The bounded retry re-finalizes the SAME path; the production
        // backend replays the committed registration, so the photo is published once
        // and the object is never deleted by the client.
        val fake = FakePhotoUploadOperations().apply { enqueueResponseLoss() }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isSuccess)
        assertEquals("photo-doc-1", result.getOrThrow().id)
        assertEquals(1, fake.uploadedPaths.size)
        assertEquals(2, fake.finalizeCalls.size)
        assertEquals(fake.finalizeCalls.first().second, fake.finalizeCalls.last().second)
    }

    @Test
    fun retryingAfterAnAmbiguousResult_replaysThePublishedPhotoWithoutReuploading() = runTest {
        // An ambiguous failure exhausts the bounded retries (the registration may or
        // may not have committed). The user then retries the exact same photo: the
        // content-derived path is already in place, so no second object is uploaded,
        // and registration replays the single published photo instead of duplicating it.
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = true, "unavailable 1")
            enqueueFailingFinalize(transient = true, "unavailable 2")
            enqueueFailingFinalize(transient = true, "unavailable 3")
            enqueueSuccessfulFinalize("photo-doc-replayed")
        }
        val first = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
        assertTrue("the ambiguous attempt fails", first.isFailure)

        val retry = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
        assertTrue("the retry succeeds by reconciling with the server", retry.isSuccess)
        assertEquals("photo-doc-replayed", retry.getOrThrow().id)
        assertEquals("no second object is uploaded", 1, fake.uploadedPaths.size)
        assertEquals(4, fake.finalizeCalls.size)
    }

    @Test
    fun existingObjectIsNotReuploaded_andRegistrationReplaysThePublishedPhoto() = runTest {
        val fake = FakePhotoUploadOperations().apply { enqueueSuccessfulFinalize() }
        val first = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
        assertTrue(first.isSuccess)

        // A second upload of the exact same content: the object is already in place, the
        // upload step is skipped, and registration replays the published photo id.
        fake.enqueueSuccessfulFinalize(photoId = first.getOrThrow().id)
        val second = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
        assertTrue(second.isSuccess)
        assertEquals(first.getOrThrow().id, second.getOrThrow().id)
        assertEquals(1, fake.uploadedPaths.size)
        assertEquals(2, fake.finalizeCalls.size)
        assertEquals(fake.finalizeCalls.first().second, fake.finalizeCalls.last().second)
    }

    @Test
    fun cancellationDuringUpload_propagatesAndNeverReportsFailure() {
        val fake = FakePhotoUploadOperations().apply {
            putObjectError = CancellationException("upload cancelled")
            enqueueSuccessfulFinalize()
        }
        val cancellation = assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
            }
        }
        assertEquals("upload cancelled", cancellation.message)
        assertEquals(0, fake.finalizeCalls.size)
    }

    @Test
    fun differentPhotoContent_usesADifferentObjectPath() = runTest {
        val otherPhoto = processedPhoto.copy(jpegBytes = byteArrayOf(0x01, 0x02, 0x03))
        val fake = FakePhotoUploadOperations().apply {
            enqueueSuccessfulFinalize("photo-a")
            enqueueSuccessfulFinalize("photo-b")
        }
        PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)
        PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", otherPhoto)

        assertEquals(2, fake.uploadedPaths.size)
        assertNotEquals(fake.uploadedPaths[0], fake.uploadedPaths[1])
    }
}
