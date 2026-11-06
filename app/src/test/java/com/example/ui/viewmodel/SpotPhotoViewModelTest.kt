package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SpotPhotoData
import com.example.data.repository.SpotPhotoRepository
import com.example.data.repository.SpotPhotosState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that [SpotPhotoViewModel] delegates the photo workflow to the repository
 * without altering results: upload success/failure, report success/duplicate-report
 * rejection, argument pass-through, and the live photo stream. UI-side gating of the
 * optional callbacks (never reporting success when no backend operation ran) is a
 * Compose concern and is exercised via the repository contract here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpotPhotoViewModelTest {

    private val testContext: Context = ApplicationProvider.getApplicationContext()
    private val pickedPhotoUri: Uri = Uri.parse("content://media/picker/1")

    private val uploadedPhoto = SpotPhotoData(
        id = "photo-doc-123",
        spotId = "spot-1",
        photoUrl = "https://storage.example.com/spot_photos/uid/spot-1/photo.jpg",
        ownerUid = "uid-1",
        reportsCount = 0,
        timestamp = 1_700_000_000_000
    )

    private inner class FakeSpotPhotoRepository : SpotPhotoRepository {
        var lastUploadedSpotId: String? = null
        var uploadResult: Result<SpotPhotoData> = Result.success(uploadedPhoto)
        var reportResult: Result<Int> = Result.success(1)
        var reportedPhotoIds = mutableListOf<String>()
        val photosFlow = MutableStateFlow<SpotPhotosState>(SpotPhotosState.Loaded(emptyList()))

        override suspend fun uploadPhoto(
            context: android.content.Context,
            spotId: String,
            uri: android.net.Uri
        ): Result<SpotPhotoData> {
            lastUploadedSpotId = spotId
            return uploadResult
        }

        override suspend fun reportPhoto(photoId: String): Result<Int> {
            reportedPhotoIds.add(photoId)
            return reportResult
        }

        override fun photosForSpotFlow(spotId: String): Flow<SpotPhotosState> = photosFlow
    }

    @Test
    fun uploadSpotPhoto_success_returnsPersistedPhotoWithRealId() = runTest {
        val fake = FakeSpotPhotoRepository()
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val result = viewModel.uploadSpotPhoto(
            context = testContext,
            spotId = "spot-1",
            uri = pickedPhotoUri
        )

        assertTrue("Upload must surface success", result.isSuccess)
        val photo = result.getOrThrow()
        assertEquals("photo-doc-123", photo.id)
        assertEquals("spot-1", photo.spotId)
        assertEquals("uid-1", photo.ownerUid)
        assertEquals("https://storage.example.com/spot_photos/uid/spot-1/photo.jpg", photo.photoUrl)
        assertEquals(0, photo.reportsCount)
        assertEquals("spot-1", fake.lastUploadedSpotId)
    }

    @Test
    fun uploadSpotPhoto_failure_propagatesBackendErrorWithoutSuccess() = runTest {
        val fake = FakeSpotPhotoRepository().apply {
            uploadResult = Result.failure(IllegalStateException("Storage upload failed"))
        }
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val result = viewModel.uploadSpotPhoto(
            context = testContext,
            spotId = "spot-1",
            uri = pickedPhotoUri
        )

        assertTrue("Upload failure must remain a failure", result.isFailure)
        assertNull(result.getOrNull())
        assertNotNull(result.exceptionOrNull())
        assertEquals("Storage upload failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun reportSpotPhoto_success_returnsNewReportCount() = runTest {
        val fake = FakeSpotPhotoRepository().apply { reportResult = Result.success(2) }
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val result = viewModel.reportSpotPhoto("photo-doc-123")

        assertTrue("Report must surface success", result.isSuccess)
        assertEquals(2, result.getOrThrow())
        assertEquals(listOf("photo-doc-123"), fake.reportedPhotoIds)
    }

    @Test
    fun reportSpotPhoto_duplicateReport_propagatesRejection() = runTest {
        val fake = FakeSpotPhotoRepository().apply {
            reportResult = Result.failure(
                IllegalStateException("User uid-1 has already reported photo photo-doc-123.")
            )
        }
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val result = viewModel.reportSpotPhoto("photo-doc-123")

        assertTrue("Duplicate report must surface as failure", result.isFailure)
        assertNull(result.getOrNull())
        assertTrue(
            "Failure must carry the duplicate-report cause",
            result.exceptionOrNull()?.message?.contains("already reported") == true
        )
    }

    @Test
    fun photosForSpotFlow_delegatesToRepositoryStream() = runTest {
        val fake = FakeSpotPhotoRepository().apply {
            photosFlow.value = SpotPhotosState.Loaded(
                listOf(
                    uploadedPhoto.copy(id = "photo-2", reportsCount = 0),
                    uploadedPhoto.copy(id = "photo-hidden", reportsCount = 5)
                )
            )
        }
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val state = viewModel.photosForSpotFlow("spot-1").first()

        assertTrue(state is SpotPhotosState.Loaded)
        val photos = (state as SpotPhotosState.Loaded).photos
        assertEquals(2, photos.size)
        assertEquals("photo-2", photos[0].id)
        // Moderation state comes from the repository stream; the VM passes it through.
        assertTrue(photos[1].isHidden)
    }

    @Test
    fun photosForSpotFlow_surfacesListenerErrorsInsteadOfAnEmptyCollection() = runTest {
        // A failed listener (e.g. a missing Firestore composite index in a fresh
        // Firebase project) must surface as an Error state — never as a Loaded empty
        // list the UI would mistake for a spot without photos.
        val fake = FakeSpotPhotoRepository().apply {
            photosFlow.value = SpotPhotosState.Error(IllegalStateException("The query requires an index"))
        }
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val state = viewModel.photosForSpotFlow("spot-1").first()

        assertTrue(state is SpotPhotosState.Error)
    }

    @Test
    fun uploadSpotPhoto_authFailure_propagatesAuthenticationError() = runTest {
        val fake = FakeSpotPhotoRepository().apply {
            uploadResult = Result.failure(IllegalStateException("Authentication required to upload photos."))
        }
        val viewModel = SpotPhotoViewModel(photoRepository = fake)

        val result = viewModel.uploadSpotPhoto(
            context = testContext,
            spotId = "spot-1",
            uri = pickedPhotoUri
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Authentication required to upload photos.",
            result.exceptionOrNull()?.message
        )
    }
}
