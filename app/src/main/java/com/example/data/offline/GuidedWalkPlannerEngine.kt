package com.example.data.offline

import com.example.data.local.ScoutSpotEntity
import com.example.engine.LisbonElevationModel
import com.example.engine.RouteElevationProfile
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
    val returnLegDistanceMeters: Double,
    val totalWalkingMinutes: Int,
    val isCircularLoop: Boolean,
    val elevationProfile: RouteElevationProfile? = null
)

/**
 * Guided Walk Planner Engine: Generates genuine circular walking loops including the return leg to the origin,
 * enforcing walking duration budgets and honest terrain elevation profiles.
 */
object GuidedWalkPlannerEngine {

    /**
     * Filters spots by mood category and accumulates waypoints forming a complete circular loop back to origin,
     * including return leg in total distance and duration. Uses honest terrain elevation data or null when unavailable.
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
            return emptyPlan(startTitle, targetDurationMinutes, mood)
        }

        // 1. Filter candidates by mood category
        val moodFiltered = availableSpots.filter { spot ->
            val catLower = spot.category.lowercase()
            val tagsLower = spot.tagsRaw.lowercase()
            mood.categories.any { catLower.contains(it) || tagsLower.contains(it) }
        }

        if (moodFiltered.isEmpty()) {
            return emptyPlan(startTitle, targetDurationMinutes, mood)
        }

        // 2. Order candidates by proximity / trajectory corridor from origin
        val sortedCandidates = moodFiltered.sortedBy { spot ->
            Geo.calculateDistanceMeters(originLat, originLng, spot.latitude, spot.longitude)
        }

        // 3. Accumulate waypoints up to target duration budget, accounting for return leg W_k -> Origin
        val selectedWaypoints = mutableListOf<ScoutSpotEntity>()
        var outboundDistanceMeters = 0.0
        var currentLat = originLat
        var currentLng = originLng

        for (candidate in sortedCandidates) {
            val legDistance = Geo.calculateDistanceMeters(currentLat, currentLng, candidate.latitude, candidate.longitude)
            val potentialOutbound = outboundDistanceMeters + legDistance
            val returnLegToOrigin = Geo.calculateDistanceMeters(candidate.latitude, candidate.longitude, originLat, originLng)
            val potentialLoopDistance = potentialOutbound + returnLegToOrigin
            val potentialLoopWalkMinutes = Geo.calculateWalkMinutes(potentialLoopDistance)

            // Duration budget constraint: stop if loop (outbound + return leg) exceeds target duration (+10% margin)
            if (selectedWaypoints.isNotEmpty() && potentialLoopWalkMinutes > (targetDurationMinutes * 1.10)) {
                break
            }

            selectedWaypoints.add(candidate)
            outboundDistanceMeters = potentialOutbound
            currentLat = candidate.latitude
            currentLng = candidate.longitude
        }

        if (selectedWaypoints.isEmpty()) {
            return emptyPlan(startTitle, targetDurationMinutes, mood)
        }

        // Return leg distance from last waypoint back to origin
        val lastWaypoint = selectedWaypoints.last()
        val returnLegMeters = Geo.calculateDistanceMeters(lastWaypoint.latitude, lastWaypoint.longitude, originLat, originLng)
        val totalLoopDistanceMeters = outboundDistanceMeters + returnLegMeters
        val totalLoopWalkingMinutes = Geo.calculateWalkMinutes(totalLoopDistanceMeters)

        // Calculate honest elevation profile along loop path points (Origin -> W1 -> W2 ... -> Wn -> Origin)
        val pathPoints = mutableListOf<Pair<Double, Double>>()
        pathPoints.add(Pair(originLat, originLng))
        for (wp in selectedWaypoints) {
            pathPoints.add(Pair(wp.latitude, wp.longitude))
        }
        pathPoints.add(Pair(originLat, originLng)) // Complete loop back to origin

        val realElevation = try {
            if (pathPoints.size >= 2) {
                LisbonElevationModel.buildElevationProfile(pathPoints)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        return GuidedWalkPlan(
            startTitle = startTitle,
            targetDurationMinutes = targetDurationMinutes,
            mood = mood,
            waypoints = selectedWaypoints,
            totalDistanceMeters = totalLoopDistanceMeters,
            returnLegDistanceMeters = returnLegMeters,
            totalWalkingMinutes = totalLoopWalkingMinutes,
            isCircularLoop = true,
            elevationProfile = realElevation
        )
    }

    private fun emptyPlan(
        startTitle: String,
        targetDurationMinutes: Int,
        mood: GuidedWalkMood
    ): GuidedWalkPlan {
        return GuidedWalkPlan(
            startTitle = startTitle,
            targetDurationMinutes = targetDurationMinutes,
            mood = mood,
            waypoints = emptyList(),
            totalDistanceMeters = 0.0,
            returnLegDistanceMeters = 0.0,
            totalWalkingMinutes = 0,
            isCircularLoop = false,
            elevationProfile = null
        )
    }
}
