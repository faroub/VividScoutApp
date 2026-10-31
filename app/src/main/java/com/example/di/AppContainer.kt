package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.location.LocationTracker
import com.example.data.offline.OfflineMapManager
import com.example.data.offline.OfflineRegionStorage
import com.example.data.repository.CommunityTipRepository
import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import com.example.engine.PreScoutEngine
import com.example.sensor.CompassSensorManager

interface AppContainer {
    val database: AppDatabase
    val scoutRepository: ScoutRepository
    val gemRepository: ScoutGemRepository
    val locationTracker: LocationTracker
    val offlineMapManager: OfflineMapManager
    val regionStorage: OfflineRegionStorage
    val communityTipRepository: CommunityTipRepository
    val preScoutEngine: PreScoutEngine
    val compassSensorManager: CompassSensorManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val scoutRepository: ScoutRepository by lazy {
        ScoutRepository(
            database.scoutDao(),
            database.discoveredGemDao(),
            database.achievementDao()
        )
    }

    override val gemRepository: ScoutGemRepository by lazy {
        ScoutGemRepository(
            database.scoutDao(),
            database.discoveredGemDao(),
            database.recentSearchDao()
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
        CommunityTipRepository(database.communityTipDao())
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
}
