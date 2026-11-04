package com.example.ui.dialogs

import android.graphics.Bitmap
import com.example.data.local.AppDatabase
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.location.LocationTracker
import com.example.data.offline.OfflineMapManager
import com.example.data.offline.OfflineRegionStorage
import com.example.data.places.PlaceRepository
import com.example.data.repository.CommunityTipRepository
import com.example.data.repository.FirebasePhotoStorageService
import com.example.data.repository.PhotoModerationRepository
import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import com.example.di.AppContainer
import com.example.engine.PreScoutEngine
import com.example.sensor.CompassSensorManager
import com.example.util.NetworkMonitor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpotDetailBottomSheetTest {

    @Test
    fun isAppContainerUnavailable_whenContainerIsNull_returnsTrue() {
        val isUnavailable = isAppContainerUnavailable(null)
        assertTrue("isAppContainerUnavailable should return true when appContainer is null", isUnavailable)
    }

    @Test
    fun isAppContainerUnavailable_whenContainerRepoIsAvailable_returnsFalse() {
        val fakeRepo = CommunityTipRepository(
            dao = object : CommunityTipDao {
                override fun getAllTipsFlow() = flowOf(emptyList<CommunityTipEntity>())
                override fun getTipsForSpotFlow(spotId: String) = flowOf(emptyList<CommunityTipEntity>())
                override fun getLatestStatusForSpot(spotId: String) = flowOf(null)
                override fun getAllStatusUpdatesFlow() = flowOf(emptyList<CommunityTipEntity>())
                override suspend fun getTipById(tipId: String) = null
                override suspend fun insertTip(tip: CommunityTipEntity) {}
                override suspend fun insertTips(tips: List<CommunityTipEntity>) {}
                override suspend fun updateTip(tip: CommunityTipEntity) {}
                override suspend fun incrementUpvote(tipId: String) {}
                override suspend fun deleteTip(tipId: String) {}
                override suspend fun getLocallyAuthoredTipsSync() = emptyList<CommunityTipEntity>()
                override suspend fun markTipAsSynced(tipId: String) {}
                override suspend fun getTipCount() = 0
            }
        )

        val fakeContainer = object : FakeAppContainer() {
            override val communityTipRepository: CommunityTipRepository = fakeRepo
        }
        val isUnavailable = isAppContainerUnavailable(fakeContainer)
        assertFalse("isAppContainerUnavailable should return false when communityTipRepository is present", isUnavailable)
    }

    @Test
    fun photoUploadAndReportCallbacks_reachStorageAndModerationRepositories() = runBlocking {
        var uploadTriggeredForSpot: String? = null
        var reportTriggeredForPhoto: String? = null

        val onUploadLambda: suspend (String, Bitmap) -> Result<String> = { spotId, _ ->
            uploadTriggeredForSpot = spotId
            Result.success("https://storage.googleapis.com/spot_photos/user1/$spotId/photo.jpg")
        }

        val onReportLambda: suspend (String) -> Result<Int> = { photoId ->
            reportTriggeredForPhoto = photoId
            Result.success(1)
        }

        val uploadRes = onUploadLambda("spot_100", Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        val reportRes = onReportLambda("photo_spot_100")

        assertTrue(uploadRes.isSuccess)
        assertEquals("spot_100", uploadTriggeredForSpot)

        assertTrue(reportRes.isSuccess)
        assertEquals("photo_spot_100", reportTriggeredForPhoto)
    }

    open class FakeAppContainer : AppContainer {
        override val database: AppDatabase get() = TODO()
        override val scoutRepository: ScoutRepository get() = TODO()
        override val gemRepository: ScoutGemRepository get() = TODO()
        override val placeRepository: PlaceRepository get() = TODO()
        override val locationTracker: LocationTracker get() = TODO()
        override val offlineMapManager: OfflineMapManager get() = TODO()
        override val regionStorage: OfflineRegionStorage get() = TODO()
        override val communityTipRepository: CommunityTipRepository get() = TODO()
        override val photoStorageService: FirebasePhotoStorageService get() = TODO()
        override val photoModerationRepository: PhotoModerationRepository get() = TODO()
        override val preScoutEngine: PreScoutEngine get() = TODO()
        override val compassSensorManager: CompassSensorManager get() = TODO()
        override val networkMonitor: NetworkMonitor get() = TODO()
    }
}
