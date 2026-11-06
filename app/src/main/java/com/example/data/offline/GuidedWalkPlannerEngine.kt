package com.example.data.offline

import com.example.data.local.ScoutSpotEntity
import com.example.engine.AStarPathfinder
import com.example.engine.CityElevationResolver
import com.example.engine.ElevationModel
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
    val elevationProfile: RouteElevationProfile? = null,
    /**
     * True only when every leg of the loop was routed on edges injected from
     * offline region packs, never on the hand-authored built-in walking graph.
     * The built-in graph connects real landmark coordinates with hand-authored
     * straight-line edges, so finding an A* path through it does not constitute
     * verified street-level pedestrian routing — such loops must be labeled
     * approximate. When false, [totalDistanceMeters] mixes or consists of
     * approximate graph distances and/or straight-line estimates.
     */
    val isFullyRouted: Boolean = false,
    /**
     * True when at least one leg fell back to a straight-line estimate because no
     * graph coverage exists for it. Distinguishes "partially unrouted" plans from
     * plans that are fully routed on (possibly approximate) graph paths.
     */
    val hasStraightLineLegs: Boolean = false
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
        } else {
            "${totalDistanceMeters.toInt()} m"
        }

    val routingLabel: String
        get() = if (isCircularLoop && isFullyRouted) {
            "Routed on offline region pack data"
        } else if (isCircularLoop && hasStraightLineLegs) {
            "Approximate · offline routing unavailable for part of this loop"
        } else if (isCircularLoop) {
            "Approximate · hand-authored walking graph, not street-verified"
        } else {
            ""
        }
}

/**
 * One leg of a guided-walk loop. [distanceMeters] is a graph path distance when
 * [isRouted] is true; otherwise it is a straight-line estimate, used only because
 * no offline routing data covers this leg. [usesOnlyRegionPackGeometry] is true
 * when the leg's path traverses only region-pack edges (plus snap connectors);
 * a routed leg on the hand-authored built-in graph is an approximate graph
 * connection, not street-verified geometry. [pathPoints] carries the leg's
 * geometry (routed polyline or straight interpolation) so distance, duration
 * and elevation all follow the same path.
 */
data class GuidedWalkLeg(
    val distanceMeters: Double,
    val isRouted: Boolean,
    val pathPoints: List<Pair<Double, Double>> = emptyList(),
    val usesOnlyRegionPackGeometry: Boolean = false
)

/**
 * Guided Walk Planner Engine: Generates genuine circular walking loops including the return leg to the origin,
 * enforcing walking duration budgets and honest terrain elevation profiles.
 *
 * Loop distances are routed on the offline walking graph ([AStarPathfinder]) whenever
 * graph coverage exists: each leg's distance is the actual graph path length. The
 * built-in graph is hand-authored (real landmark nodes connected by straight-line
 * edges), so a loop routed only on it is an approximate graph connection, not a
 * verified street-level pedestrian route — such plans keep
 * [GuidedWalkPlan.isFullyRouted] = false and are labeled accordingly. Only loops
 * whose legs run exclusively on edges injected from offline region packs qualify as
 * fully routed. When a leg has no graph coverage at all it falls back to a
 * clearly-labeled straight-line estimate, marking the plan approximate.
 */
object GuidedWalkPlannerEngine {

    /**
     * Routes one loop leg on the pedestrian network. Falls back to a straight-line
     * estimate (marked [GuidedWalkLeg.isRouted] = false) when either endpoint lies
     * outside the graph's coverage or the coordinates are unusable.
     */
    private fun routeLeg(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        fromName: String,
        toName: String
    ): GuidedWalkLeg {
        val direct = Geo.calculateDistanceMeters(fromLat, fromLng, toLat, toLng)
        if (direct.isNaN()) return GuidedWalkLeg(0.0, false)

        val result = AStarPathfinder.findShortestPath(
            startLat = fromLat,
            startLng = fromLng,
            destLat = toLat,
            destLng = toLng,
            startName = fromName,
            destName = toName
        )
        return GuidedWalkLeg(
            distanceMeters = result.totalDistanceMeters,
            isRouted = !result.isDirectFallback,
            pathPoints = result.pathPoints,
            usesOnlyRegionPackGeometry = result.usesOnlyRegionPackGeometry
        )
    }

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
        availableSpots: List<ScoutSpotEntity>,
        cityName: String? = null,
        elevationModel: ElevationModel? = CityElevationResolver.resolveModelForCity(cityName, originLat, originLng)
    ): GuidedWalkPlan {
        // Validate duration budget & origin coordinates
        if (targetDurationMinutes <= 0) {
            return emptyPlan(startTitle, targetDurationMinutes, mood)
        }
        if (originLat.isNaN() || originLng.isNaN() ||
            originLat < -90.0 || originLat > 90.0 ||
            originLng < -180.0 || originLng > 180.0 ||
            (originLat == 0.0 && originLng == 0.0)) {
            return emptyPlan(startTitle, targetDurationMinutes, mood)
        }

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

        // 3. Accumulate waypoints up to target duration budget, accounting for return leg W_k -> Origin.
        //    Every leg is routed on the pedestrian network where coverage exists; budget
        //    and totals use the routed distances, and unrouted legs mark the plan approximate.
        val selectedWaypoints = mutableListOf<ScoutSpotEntity>()
        val legs = mutableListOf<GuidedWalkLeg>()
        var outboundDistanceMeters = 0.0
        var currentLat = originLat
        var currentLng = originLng
        var currentTitle = startTitle
        var lastReturnLeg: GuidedWalkLeg? = null

        for (candidate in sortedCandidates) {
            // Validate candidate coordinates
            if (candidate.latitude.isNaN() || candidate.longitude.isNaN() ||
                candidate.latitude < -90.0 || candidate.latitude > 90.0 ||
                candidate.longitude < -180.0 || candidate.longitude > 180.0 ||
                (candidate.latitude == 0.0 && candidate.longitude == 0.0)) {
                continue
            }

            val outLeg = routeLeg(currentLat, currentLng, candidate.latitude, candidate.longitude, currentTitle, candidate.title)
            val returnLeg = routeLeg(candidate.latitude, candidate.longitude, originLat, originLng, candidate.title, startTitle)
            if (outLeg.distanceMeters.isNaN() || returnLeg.distanceMeters.isNaN()) continue

            val potentialOutbound = outboundDistanceMeters + outLeg.distanceMeters
            val potentialLoopDistance = potentialOutbound + returnLeg.distanceMeters
            val potentialLoopWalkMinutes = Geo.calculateWalkMinutes(potentialLoopDistance)

            // Duration budget constraint applied to ALL waypoints (first and subsequent).
            // Skip infeasible candidates without stopping search so later feasible candidates can still be included.
            if (potentialLoopWalkMinutes > (targetDurationMinutes * 1.10)) {
                continue
            }

            selectedWaypoints.add(candidate)
            legs.add(outLeg)
            outboundDistanceMeters = potentialOutbound
            currentLat = candidate.latitude
            currentLng = candidate.longitude
            currentTitle = candidate.title
            lastReturnLeg = returnLeg
        }

        if (selectedWaypoints.isEmpty()) {
            return emptyPlan(startTitle, targetDurationMinutes, mood)
        }

        // Return leg from last waypoint back to origin
        val returnLeg = lastReturnLeg ?: routeLeg(
            currentLat, currentLng, originLat, originLng, currentTitle, startTitle
        )
        legs.add(returnLeg)
        val returnLegMeters = returnLeg.distanceMeters
        val totalLoopDistanceMeters = outboundDistanceMeters + returnLegMeters
        val totalLoopWalkingMinutes = Geo.calculateWalkMinutes(totalLoopDistanceMeters)

        // Calculate honest elevation profile along the routed loop path
        // (Origin -> leg geometry -> ... -> Origin), so distance, duration and
        // elevation all follow the same pedestrian path.
        val pathPoints = mutableListOf<Pair<Double, Double>>()
        pathPoints.add(Pair(originLat, originLng))
        for (leg in legs) {
            pathPoints.addAll(leg.pathPoints.drop(1))
        }
        if (pathPoints.isEmpty() || pathPoints.last() != Pair(originLat, originLng)) {
            pathPoints.add(Pair(originLat, originLng)) // Complete loop back to origin
        }

        val realElevation = try {
            if (elevationModel != null && elevationModel.isSupported && pathPoints.size >= 2) {
                elevationModel.buildElevationProfile(pathPoints)
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
            elevationProfile = realElevation,
            isFullyRouted = legs.isNotEmpty() && legs.all { it.isRouted && it.usesOnlyRegionPackGeometry },
            hasStraightLineLegs = legs.any { !it.isRouted }
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
