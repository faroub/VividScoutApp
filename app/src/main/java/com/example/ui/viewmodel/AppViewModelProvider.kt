package com.example.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.VividScoutApplication

object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            val container = vividScoutApplication().container
            LocationViewModel(
                locationTracker = container.locationTracker
            )
        }
        initializer {
            val container = vividScoutApplication().container
            ExploreViewModel(
                repository = container.scoutRepository,
                gemRepository = container.gemRepository,
                preScoutEngine = container.preScoutEngine
            )
        }
        initializer {
            val container = vividScoutApplication().container
            RadarViewModel(
                repository = container.scoutRepository,
                compassSensorManager = container.compassSensorManager
            )
        }
        initializer {
            val container = vividScoutApplication().container
            CommunityViewModel(
                communityRepository = container.communityTipRepository
            )
        }
        initializer {
            val container = vividScoutApplication().container
            OfflineMapsViewModel(
                offlineMapManager = container.offlineMapManager,
                regionStorage = container.regionStorage,
                repository = container.scoutRepository
            )
        }
        initializer {
            val container = vividScoutApplication().container
            ProfileViewModel(
                repository = container.scoutRepository
            )
        }
        initializer {
            val container = vividScoutApplication().container
            MainViewModel(
                repository = container.scoutRepository
            )
        }
    }
}

fun CreationExtras.vividScoutApplication(): VividScoutApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VividScoutApplication)
