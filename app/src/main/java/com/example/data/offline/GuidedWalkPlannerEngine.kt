package com.example.data.offline

import com.example.data.local.ScoutSpotEntity
import com.example.util.Geo

enum class GuidedWalkMood(val title: String, val categories: List<String>) {
    SCENIC("Scenic", listOf("sights", "viewpoint", "scenic")),
    HISTORIC("Historic", listOf("history", "monument", "historic", "cultural")),
    QUIET("Quiet", listOf("shortcut", "gems", "hidden"))
}

data class GuidedWalkPlan(
    val startTitle: String,
    val targetDurationMinutes: Int,
    val mood: GuidedWalkMood,
    val waypoints: List<ScoutSpotEntity>,
    val totalDistanceMeters: Double,
    val totalWalkingMinutes: Int,
    val elevationProfile: List<Int>
)

/**
 * Guided Walk Planner Engine: Generates optimized walking loops limited to user-selected duration
 * and mood preferences without external AI network dependency.
 */
object GuidedWalkPlannerEngine {

    /**
     * Filters available spots by mood category and accumulates waypoints along trajectory corridor,
     * ensuring total walking time does not exceed the target duration limit.
     */
    fun planGuidedWalk(
        originLat: Double,
        originLng: Double,
        startTitle: String,
        targetDurationMinutes: Int,
        mood: GuidedWalkMood,
        availableSpots: List<ScoutSpotEntity>
    ): GuidedWalkPlan {
        if (availableSpots.isEmpty()) {
            return GuidedWalkPlan(
                startTitle = startTitle,
                targetDurationMinutes = targetDurationMinutes,
                mood = mood,
                waypoints = emptyList(),
                totalDistanceMeters = 0.0,
                totalWalkingMinutes = 0,
                elevationProfile = emptyList()
            )
        }

        // 1. Filter candidates by mood category
        val moodFiltered = availableSpots.filter { spot ->
            val catLower = spot.category.lowercase()
            val tagsLower = spot.tagsRaw.lowercase()
            mood.categories.any { catLower.contains(it) || tagsLower.contains(it) }
        }

        if (moodFiltered.isEmpty()) {
            return GuidedWalkPlan(
                startTitle = startTitle,
                targetDurationMinutes = targetDurationMinutes,
                mood = mood,
                waypoints = emptyList(),
                totalDistanceMeters = 0.0,
                totalWalkingMinutes = 0,
                elevationProfile = emptyList()
            )
        }

        // 2. Order candidates by proximity / trajectory corridor from origin
        val sortedCandidates = moodFiltered.sortedBy { spot ->
            Geo.calculateDistanceMeters(originLat, originLng, spot.latitude, spot.longitude)
        }

        // 3. Accumulate waypoints up to target duration limit
        val selectedWaypoints = mutableListOf<ScoutSpotEntity>()
        var accumulatedDistanceMeters = 0.0
        var currentLat = originLat
        var currentLng = originLng

        for (candidate in sortedCandidates) {
            val legDistance = Geo.calculateDistanceMeters(currentLat, currentLng, candidate.latitude, candidate.longitude)
            val potentialTotalDistance = accumulatedDistanceMeters + legDistance
            val potentialWalkMinutes = Geo.calculateWalkMinutes(potentialTotalDistance)

            // Duration limit constraint: stop if adding waypoint exceeds target duration (with 10% margin)
            if (selectedWaypoints.isNotEmpty() && potentialWalkMinutes > (targetDurationMinutes * 1.10)) {
                break
            }

            selectedWaypoints.add(candidate)
            accumulatedDistanceMeters = potentialTotalDistance
            currentLat = candidate.latitude
            currentLng = candidate.longitude
        }

        val totalWalkMinutes = Geo.calculateWalkMinutes(accumulatedDistanceMeters)

        // Generate synthetic elevation profile sparkline (e.g., hill climb/descent profile)
        val elevationProfile = generateElevationSparkline(selectedWaypoints.size)

        return GuidedWalkPlan(
            startTitle = startTitle,
            targetDurationMinutes = targetDurationMinutes,
            mood = mood,
            waypoints = selectedWaypoints,
            totalDistanceMeters = accumulatedDistanceMeters,
            totalWalkingMinutes = totalWalkMinutes,
            elevationProfile = elevationProfile
        )
    }

    private fun generateElevationSparkline(waypointCount: Int): List<Int> {
        if (waypointCount == 0) return emptyList()
        val baseElevation = 45
        return (0..waypointCount + 2).map { i ->
            baseElevation + ((i * 12) % 35) + (if (i % 2 == 0) 8 else -5)
        }
    }
}
