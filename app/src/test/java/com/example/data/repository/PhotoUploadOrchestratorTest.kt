package com.example.data.repository

import com.example.util.ProcessedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the client-side orphan control of [PhotoUploadOrchestrator] against a fake
 * [PhotoUploadOperations]: failed or interrupted registrations delete the uploaded
 * object again, transient registration failures are retried bounded (without ever
 * creating a duplicate photo — the backend replays registered objects), definitive
 * rejections fail immediately, and cancellation never reports success.
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
        var deleteError: Exception? = null
        val deletedPaths = mutableListOf<String>()
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

        override suspend fun deleteObject(path: String) {
            deleteError?.let { throw it }
            deletedPaths.add(path)
        }

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
    }

    @Test
    fun successfulUpload_registersAndDeletesNothing() = runTest {
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
        assertTrue("A successful upload must never delete its object", fake.deletedPaths.isEmpty())
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
        assertTrue("Successful completion must not delete the object", fake.deletedPaths.isEmpty())
    }

    @Test
    fun permanentFinalizeFailure_failsImmediatelyAndCleansUpTheOrphan() = runTest {
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
            "The orphaned object must be deleted after a failed registration",
            1,
            fake.deletedPaths.size
        )
        assertEquals(fake.uploadedPaths.first(), fake.deletedPaths.first())
    }

    @Test
    fun allFinalizeAttemptsFailing_deletesTheOrphanAndReportsTheError() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = true, "unavailable 1")
            enqueueFailingFinalize(transient = true, "unavailable 2")
            enqueueFailingFinalize(transient = true, "unavailable 3")
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        assertEquals("the bounded retry must stop at 3 attempts", 3, fake.finalizeCalls.size)
        assertEquals("the orphan must be cleaned up", 1, fake.deletedPaths.size)
    }

    @Test
    fun putObjectFailure_reportsFailureWithoutCleanupOrRegistration() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            putObjectError = IllegalStateException("storage write failed")
            enqueueSuccessfulFinalize()
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        assertEquals("storage write failed", result.exceptionOrNull()?.message)
        assertEquals(
            "Nothing was uploaded, so there must be nothing to delete",
            0,
            fake.deletedPaths.size
        )
        assertEquals("Registration must never run when the upload failed", 0, fake.finalizeCalls.size)
    }

    @Test
    fun cleanupFailure_neverMasksTheOriginalUploadError() = runTest {
        val fake = FakePhotoUploadOperations().apply {
            enqueueFailingFinalize(transient = true, "unavailable 1")
            enqueueFailingFinalize(transient = true, "unavailable 2")
            enqueueFailingFinalize(transient = true, "unavailable 3")
            deleteError = IllegalStateException("delete failed")
        }
        val result = PhotoUploadOrchestrator(fake).uploadPhoto("spot-1", "uid-1", processedPhoto)

        assertTrue(result.isFailure)
        assertEquals(
            "The registration error must survive a failed cleanup",
            "unavailable 3",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun cancellationAfterUpload_cleansUpAndPropagates() {
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
            "An interrupted upload must clean up its object before propagating",
            1,
            fake.deletedPaths.size
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
}
