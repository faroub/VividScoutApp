package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.location.LocationTracker
import com.example.data.network.OverpassScraperService
import com.example.data.network.WikidataSearchService
import com.example.data.offline.OfflineMapManager
import com.example.data.offline.OfflineRegionStorage
import com.example.data.places.GeonamesSource
import com.example.data.places.OpenTripMapSource
import com.example.data.places.PlaceRepository
import com.example.data.repository.CommunityTipRepository
import com.example.data.repository.FirebasePhotoStorageService
import com.example.data.repository.PhotoModerationRepository
import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import com.example.data.repository.SpotPhotoRepository
import com.example.engine.PreScoutEngine
import com.example.sensor.CompassSensorManager

interface AppContainer {
    val database: AppDatabase
    val scoutRepository: ScoutRepository
    val gemRepository: ScoutGemRepository
    val placeRepository: PlaceRepository
    val locationTracker: LocationTracker
    val offlineMapManager: OfflineMapManager
    val regionStorage: OfflineRegionStorage
    val communityTipRepository: CommunityTipRepository
    val photoStorageService: FirebasePhotoStorageService
    val photoModerationRepository: SpotPhotoRepository
    val preScoutEngine: PreScoutEngine
    val compassSensorManager: CompassSensorManager
    val networkMonitor: com.example.util.NetworkMonitor
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val scoutRepository: ScoutRepository by lazy {
        ScoutRepository(
            database.scoutDao(),
            database.discoveredGemDao(),
            database.achievementDao(),
            context
        )
    }

    override val placeRepository: PlaceRepository by lazy {
        PlaceRepository(
            cachedPlaceDao = database.cachedPlaceDao(),
            placeSources = listOf(
                OverpassScraperService(),
                WikidataSearchService(),
                OpenTripMapSource(),
                GeonamesSource()
            )
        )
    }

    override val gemRepository: ScoutGemRepository by lazy {
        ScoutGemRepository(
            scoutDao = database.scoutDao(),
            discoveredGemDao = database.discoveredGemDao(),
            recentSearchDao = database.recentSearchDao(),
            placeRepository = placeRepository
        )
    }

    override val locationTracker: LocationTracker by lazy {
        LocationTracker(context)
    }

    override val offlineMapManager: OfflineMapManager by lazy {
        OfflineMapManager(context, gemRepository)
    }

    override val regionStorage: OfflineRegionStorage by lazy {
        OfflineRegionStorage(context)
    }

    override val communityTipRepository: CommunityTipRepository by lazy {
        CommunityTipRepository(
            dao = database.communityTipDao(),
            tipVoteDao = database.tipVoteDao(),
            context = context
        )
    }

    override val photoStorageService: FirebasePhotoStorageService by lazy {
        FirebasePhotoStorageService()
    }

    override val photoModerationRepository: SpotPhotoRepository by lazy {
        PhotoModerationRepository(storageService = photoStorageService)
    }

    override val preScoutEngine: PreScoutEngine by lazy {
        PreScoutEngine(
            scoutDao = database.scoutDao(),
            recentSearchDao = database.recentSearchDao(),
            gemRepository = gemRepository
        )
    }

    override val compassSensorManager: CompassSensorManager by lazy {
        CompassSensorManager(context)
    }

    override val networkMonitor: com.example.util.NetworkMonitor by lazy {
        com.example.util.NetworkMonitor(context)
    }
}
