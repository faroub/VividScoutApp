package com.example.ui.dialogs

import com.example.data.local.AppDatabase
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.location.LocationTracker
import com.example.data.offline.OfflineMapManager
import com.example.data.offline.OfflineRegionStorage
import com.example.data.places.PlaceRepository
import com.example.data.repository.CommunityTipRepository
import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import com.example.di.AppContainer
import com.example.engine.PreScoutEngine
import com.example.sensor.CompassSensorManager
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    open class FakeAppContainer : AppContainer {
        override val database: AppDatabase get() = TODO()
        override val scoutRepository: ScoutRepository get() = TODO()
        override val gemRepository: ScoutGemRepository get() = TODO()
        override val placeRepository: PlaceRepository get() = TODO()
        override val locationTracker: LocationTracker get() = TODO()
        override val offlineMapManager: OfflineMapManager get() = TODO()
        override val regionStorage: OfflineRegionStorage get() = TODO()
        override val communityTipRepository: CommunityTipRepository get() = TODO()
        override val preScoutEngine: PreScoutEngine get() = TODO()
        override val compassSensorManager: CompassSensorManager get() = TODO()
        override val networkMonitor: com.example.util.NetworkMonitor get() = TODO()
    }
}
