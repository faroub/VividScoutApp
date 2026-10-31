package com.example.ui.screens

import androidx.compose.runtime.Immutable
import com.example.data.local.ScoutSpotEntity

@Immutable
data class ScoutSpotCardState(
    val spot: ScoutSpotEntity,
    val isPlayingAudio: Boolean = false,
    val userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423)
)

@Immutable
data class ExploreFilterState(
    val selectedCategory: String = "all",
    val searchQuery: String = ""
)

@Immutable
data class ExploreHeaderState(
    val headerStyle: Int = 0,
    val isDiscovering: Boolean = false,
    val discoveryMessage: String? = null
)
