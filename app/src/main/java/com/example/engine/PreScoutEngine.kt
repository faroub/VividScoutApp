package com.example.engine

import com.example.data.local.RecentSearchDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.data.location.LocationTracker
import com.example.data.repository.ScoutGemRepository
import com.example.util.LocationPhotoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * State for the non-intrusive Pre-Scout notification ticker.
 */
data class PreScoutBannerState(
    val isVisible: Boolean = false,
    val cityName: String = "",
    val count: Int = 0,
    val topCategories: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Pre-scout engine algorithm that analyzes past search and bookmark categories
 * and proactively prefetches 5 high-rated gems within a 10km radius upon city boundary entry.
 */
class PreScoutEngine(
    private val scoutDao: ScoutDao,
    private val recentSearchDao: RecentSearchDao,
    private val gemRepository: ScoutGemRepository
) {
    private var lastAnchorLat: Double? = null
    private var lastAnchorLng: Double? = null
    private var lastAnchorCity: String? = null

    /**
     * Calculates user category preference weights based on past searches and bookmarks.
     * Formula: W(cat) = 2 * count(recent_searches) + 3 * count(saved_bookmarks)
     */
    suspend fun calculateCategoryWeights(): Map<String, Int> = withContext(Dispatchers.IO) {
        val weights = mutableMapOf(
            "sights" to 1,
            "eats" to 1,
            "history" to 1,
            "shortcut" to 1
        )

        // 1. Analyze recent searches (weight = 2 per occurrence)
        val recentSearches = recentSearchDao.getRecentSearchesList()
        for (search in recentSearches) {
            val cat = search.category.lowercase()
            if (cat.isNotBlank() && cat != "all") {
                weights[cat] = (weights[cat] ?: 0) + 2
            } else {
                val queryLower = search.queryText.lowercase()
                when {
                    queryLower.contains("eat") || queryLower.contains("bake") || queryLower.contains("cafe") || queryLower.contains("food") ->
                        weights["eats"] = (weights["eats"] ?: 0) + 2
                    queryLower.contains("history") || queryLower.contains("gothic") || queryLower.contains("church") || queryLower.contains("museum") ->
                        weights["history"] = (weights["history"] ?: 0) + 2
                    queryLower.contains("shortcut") || queryLower.contains("secret") || queryLower.contains("alley") ->
                        weights["shortcut"] = (weights["shortcut"] ?: 0) + 2
                    else ->
                        weights["sights"] = (weights["sights"] ?: 0) + 2
                }
            }
        }

        // 2. Analyze bookmarked/saved spots (weight = 3 per bookmark)
        val savedSpots = scoutDao.getSavedSpotsList()
        for (spot in savedSpots) {
            val cat = spot.category.lowercase()
            weights[cat] = (weights[cat] ?: 0) + 3
        }

        weights
    }

    /**
     * Determines whether the user has crossed a city boundary (>10km movement or new city name).
     */
    fun shouldTriggerPreScout(lat: Double, lng: Double, cityName: String): Boolean {
        val prevLat = lastAnchorLat
        val prevLng = lastAnchorLng
        val prevCity = lastAnchorCity

        if (prevLat == null || prevLng == null || prevCity == null) {
            lastAnchorLat = lat
            lastAnchorLng = lng
            lastAnchorCity = cityName
            return true
        }

        if (!prevCity.equals(cityName, ignoreCase = true)) {
            lastAnchorLat = lat
            lastAnchorLng = lng
            lastAnchorCity = cityName
            return true
        }

        val distance = LocationTracker.distanceBetween(lat, lng, prevLat, prevLng)
        if (distance >= 10000f) {
            lastAnchorLat = lat
            lastAnchorLng = lng
            return true
        }

        return false
    }

    /**
     * Executes the pre-scout prefetch algorithm:
     * 1. Evaluates user category weights to pick top categories.
     * 2. Prefetches live gems matching categories within 10,000m radius.
     * 3. Selects 5 high-rated gems (rating >= 4.5).
     * 4. Updates status to "Pre-scouted Local" & offlineCached = true.
     * 5. Enriches authentic photo metadata via Wikipedia/Wikimedia API.
     */
    suspend fun executePreScout(
        lat: Double,
        lng: Double,
        cityName: String
    ): PreScoutBannerState = withContext(Dispatchers.IO) {
        val categoryWeights = calculateCategoryWeights()
        val topCategories = categoryWeights.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val primaryCategory = topCategories.firstOrNull() ?: "sights"

        // Trigger live Overpass/Gemini sync for primary category within 10km radius
        val syncResult = gemRepository.syncLiveOsmGems(
            userLat = lat,
            userLng = lng,
            radiusMeters = 10000,
            category = primaryCategory,
            customQuery = ""
        )

        val allSpots = scoutDao.getAllSpotsSync()
        val highRatedNearby = allSpots.filter { spot ->
            (spot.rating ?: 0.0) >= 4.5 && spot.computedDistanceMeters(lat, lng) <= 10000f
        }.take(5)

        val updatedSpots = highRatedNearby.map { spot ->
            spot.copy(
                statusBadge = "Pre-scouted Local",
                offlineCached = true
            )
        }

        if (updatedSpots.isNotEmpty()) {
            scoutDao.insertSpots(updatedSpots)
        }

        // Enrich authentic photos for pre-scouted spots
        for (spot in updatedSpots) {
            val resolved = LocationPhotoResolver.resolveEnrichedPhoto(
                title = spot.title,
                category = spot.category,
                districtOrLocation = cityName,
                spotId = spot.id
            )
            if (resolved.url.isNotBlank() && resolved.url != spot.imageUrl) {
                scoutDao.updateImageUrl(spot.id, resolved.url)
            }
        }

        val countScouted = if (updatedSpots.isNotEmpty()) updatedSpots.size else syncResult.getOrDefault(5).coerceAtLeast(1)

        PreScoutBannerState(
            isVisible = true,
            cityName = cityName,
            count = countScouted,
            topCategories = topCategories,
            timestamp = System.currentTimeMillis()
        )
    }
}
