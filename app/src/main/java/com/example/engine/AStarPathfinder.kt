package com.example.engine

import androidx.annotation.Keep
import com.example.engine.OfflineRouteEngine.haversineMeters
import java.util.PriorityQueue
import kotlin.math.roundToInt

@Keep
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val streetName: String,
    val isPedestrianOnly: Boolean = false,
    val isStairsOrCalçada: Boolean = false
) {
    val formattedDistance: String
        get() = if (distanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.roundToInt()} m"
        }
}

@Keep
data class AStarRouteResult(
    val originName: String,
    val destinationName: String,
    val pathPoints: List<Pair<Double, Double>>,
    val waypoints: List<WalkNode>,
    val steps: List<RouteStep>,
    val totalDistanceMeters: Double,
    val estimatedWalkingMinutes: Int
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
        } else {
            "${totalDistanceMeters.roundToInt()} m"
        }

    val formattedDuration: String
        get() = if (estimatedWalkingMinutes >= 60) {
            val hrs = estimatedWalkingMinutes / 60
            val mins = estimatedWalkingMinutes % 60
            "${hrs}h ${mins}m"
        } else {
            "$estimatedWalkingMinutes mins"
        }
}

/**
 * 100% Offline A* Pathfinding Engine for pedestrian street navigation.
 * Snaps arbitrary GPS points to the vector road network, calculates optimal
 * paths using Haversine distance heuristics, and produces turn-by-turn walking steps.
 */
object AStarPathfinder {

    // Standard average walking speed: 4.8 km/h (~80 meters / minute)
    private const val WALKING_SPEED_METERS_PER_MIN = 80.0

    private data class PriorityNode(
        val nodeId: String,
        val fScore: Double
    ) : Comparable<PriorityNode> {
        override fun compareTo(other: PriorityNode): Int = fScore.compareTo(other.fScore)
    }

    private data class CameFromEntry(
        val previousNodeId: String,
        val edge: WalkEdge
    )

    /**
     * Calculates the shortest walking path between any two locations using A* graph search.
     */
    fun findShortestPath(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        startName: String = "Starting Point",
        destName: String = "Destination"
    ): AStarRouteResult {
        // Direct distance check
        val directDist = haversineMeters(startLat, startLng, destLat, destLng)
        if (directDist < 10.0) {
            // Already at destination
            return AStarRouteResult(
                originName = startName,
                destinationName = destName,
                pathPoints = listOf(Pair(startLat, startLng), Pair(destLat, destLng)),
                waypoints = listOf(
                    WalkNode("origin", startLat, startLng, startName),
                    WalkNode("dest", destLat, destLng, destName)
                ),
                steps = listOf(
                    RouteStep(
                        instruction = "You have arrived at $destName",
                        distanceMeters = directDist,
                        streetName = "Destination",
                        isPedestrianOnly = true
                    )
                ),
                totalDistanceMeters = directDist,
                estimatedWalkingMinutes = 1
            )
        }

        val allNodes = VectorWalkGraph.getNodes().toMutableMap()
        val allEdges = VectorWalkGraph.getEdges().toMutableList()

        // 1. Create virtual origin and destination nodes
        val originId = "virtual_origin"
        val destId = "virtual_dest"

        val originNode = WalkNode(originId, startLat, startLng, startName)
        val destNode = WalkNode(destId, destLat, destLng, destName)

        allNodes[originId] = originNode
        allNodes[destId] = destNode

        // 2. Snap Origin and Destination to closest base graph nodes
        val closestOriginNodes = findKClosestNodes(startLat, startLng, allNodes.values.filter { it.id != originId && it.id != destId }, 3)
        val closestDestNodes = findKClosestNodes(destLat, destLng, allNodes.values.filter { it.id != originId && it.id != destId }, 3)

        closestOriginNodes.forEach { targetNode ->
            val d = haversineMeters(startLat, startLng, targetNode.lat, targetNode.lng)
            allEdges.add(WalkEdge(originId, targetNode.id, d, "Walk towards ${targetNode.name}", isPedestrianOnly = true))
            allEdges.add(WalkEdge(targetNode.id, originId, d, "Walk towards $startName", isPedestrianOnly = true))
        }

        closestDestNodes.forEach { targetNode ->
            val d = haversineMeters(targetNode.lat, targetNode.lng, destLat, destLng)
            allEdges.add(WalkEdge(targetNode.id, destId, d, "Approach $destName", isPedestrianOnly = true))
            allEdges.add(WalkEdge(destId, targetNode.id, d, "Walk towards ${targetNode.name}", isPedestrianOnly = true))
        }

        // Build adjacency map
        val adjacency = mutableMapOf<String, MutableList<WalkEdge>>()
        for (edge in allEdges) {
            adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge)
        }

        // 3. A* Search Execution
        val gScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        gScore[originId] = 0.0

        val fScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val initialH = haversineMeters(startLat, startLng, destLat, destLng)
        fScore[originId] = initialH

        val openSet = PriorityQueue<PriorityNode>()
        openSet.add(PriorityNode(originId, initialH))

        val cameFrom = mutableMapOf<String, CameFromEntry>()
        val closedSet = mutableSetOf<String>()

        var foundTarget = false

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            val currentId = current.nodeId

            if (currentId == destId) {
                foundTarget = true
                break
            }

            if (!closedSet.add(currentId)) {
                continue
            }

            val currentG = gScore[currentId] ?: Double.POSITIVE_INFINITY

            val neighbors = adjacency[currentId] ?: emptyList()
            for (edge in neighbors) {
                val neighborId = edge.toId
                if (neighborId in closedSet) continue

                val tentativeG = currentG + edge.distanceMeters
                val neighborG = gScore.getValue(neighborId)

                if (tentativeG < neighborG) {
                    cameFrom[neighborId] = CameFromEntry(currentId, edge)
                    gScore[neighborId] = tentativeG

                    val neighborNode = allNodes[neighborId]
                    val h = if (neighborNode != null) {
                        haversineMeters(neighborNode.lat, neighborNode.lng, destLat, destLng)
                    } else 0.0

                    val newF = tentativeG + h
                    fScore[neighborId] = newF
                    openSet.add(PriorityNode(neighborId, newF))
                }
            }
        }

        // 4. Reconstruct path or fallback to direct trail
        if (!foundTarget) {
            return generateDirectFallbackRoute(
                startLat = startLat,
                startLng = startLng,
                destLat = destLat,
                destLng = destLng,
                startName = startName,
                destName = destName,
                distance = directDist
            )
        }

        // Trace back from destId to originId
        val reversedPathNodes = mutableListOf<WalkNode>()
        val reversedEdges = mutableListOf<WalkEdge>()

        var curr = destId
        while (curr != originId) {
            val node = allNodes[curr]
            if (node != null) reversedPathNodes.add(node)

            val entry = cameFrom[curr] ?: break
            reversedEdges.add(entry.edge)
            curr = entry.previousNodeId
        }
        allNodes[originId]?.let { reversedPathNodes.add(it) }

        val pathNodes = reversedPathNodes.reversed()
        val pathEdges = reversedEdges.reversed()

        // 5. Interpolate polyline points for smooth vector canvas rendering
        val pathPoints = mutableListOf<Pair<Double, Double>>()
        var totalDistance = 0.0

        for (i in 0 until pathNodes.size - 1) {
            val a = pathNodes[i]
            val b = pathNodes[i + 1]
            val segDist = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            totalDistance += segDist

            // Add starting point
            if (i == 0) {
                pathPoints.add(Pair(a.lat, a.lng))
            }

            // Interpolate points every ~25 meters for curved street rendering
            val subSteps = (segDist / 25.0).roundToInt().coerceIn(1, 6)
            for (step in 1..subSteps) {
                val fraction = step.toDouble() / subSteps
                val lat = a.lat + (b.lat - a.lat) * fraction
                val lng = a.lng + (b.lng - a.lng) * fraction
                pathPoints.add(Pair(lat, lng))
            }
        }

        // 6. Generate Consolidated Human-Readable Turn-by-Turn Steps
        val steps = consolidateSteps(pathEdges, destName)
        val minutes = (totalDistance / WALKING_SPEED_METERS_PER_MIN).roundToInt().coerceAtLeast(1)

        return AStarRouteResult(
            originName = startName,
            destinationName = destName,
            pathPoints = pathPoints,
            waypoints = pathNodes,
            steps = steps,
            totalDistanceMeters = totalDistance,
            estimatedWalkingMinutes = minutes
        )
    }

    private fun findKClosestNodes(lat: Double, lng: Double, nodes: Iterable<WalkNode>, k: Int): List<WalkNode> {
        return nodes.sortedBy { node ->
            haversineMeters(lat, lng, node.lat, node.lng)
        }.take(k)
    }

    private fun consolidateSteps(edges: List<WalkEdge>, destName: String): List<RouteStep> {
        if (edges.isEmpty()) {
            return listOf(
                RouteStep(
                    instruction = "Arrive at $destName",
                    distanceMeters = 0.0,
                    streetName = destName,
                    isPedestrianOnly = true
                )
            )
        }

        val consolidated = mutableListOf<RouteStep>()
        var currentStreet = edges.first().streetName
        var currentDist = 0.0
        var isPedestrian = edges.first().isPedestrianOnly
        var isStairs = edges.first().isStairsOrCalçada

        for (edge in edges) {
            if (edge.streetName == currentStreet) {
                currentDist += edge.distanceMeters
                if (edge.isPedestrianOnly) isPedestrian = true
                if (edge.isStairsOrCalçada) isStairs = true
            } else {
                val instruction = buildInstruction(currentStreet, currentDist, isPedestrian, isStairs)
                consolidated.add(
                    RouteStep(
                        instruction = instruction,
                        distanceMeters = currentDist,
                        streetName = currentStreet,
                        isPedestrianOnly = isPedestrian,
                        isStairsOrCalçada = isStairs
                    )
                )

                currentStreet = edge.streetName
                currentDist = edge.distanceMeters
                isPedestrian = edge.isPedestrianOnly
                isStairs = edge.isStairsOrCalçada
            }
        }

        // Add last accumulated step
        val lastInstruction = buildInstruction(currentStreet, currentDist, isPedestrian, isStairs)
        consolidated.add(
            RouteStep(
                instruction = lastInstruction,
                distanceMeters = currentDist,
                streetName = currentStreet,
                isPedestrianOnly = isPedestrian,
                isStairsOrCalçada = isStairs
            )
        )

        // Add final arrival step
        consolidated.add(
            RouteStep(
                instruction = "Arrive at destination: $destName",
                distanceMeters = 0.0,
                streetName = destName,
                isPedestrianOnly = true
            )
        )

        return consolidated
    }

    private fun buildInstruction(street: String, dist: Double, isPedestrian: Boolean, isStairs: Boolean): String {
        val distStr = if (dist >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", dist / 1000.0)
        } else {
            "${dist.roundToInt()} m"
        }

        return when {
            isStairs -> "Ascend/Descend $street for $distStr (stone steps)"
            isPedestrian -> "Walk along pedestrian walkway $street for $distStr"
            else -> "Follow $street for $distStr"
        }
    }

    private fun generateDirectFallbackRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        startName: String,
        destName: String,
        distance: Double
    ): AStarRouteResult {
        val steps = 5
        val pathPoints = mutableListOf<Pair<Double, Double>>()
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val lat = startLat + (destLat - startLat) * fraction
            val lng = startLng + (destLng - startLng) * fraction
            pathPoints.add(Pair(lat, lng))
        }

        val minutes = (distance / WALKING_SPEED_METERS_PER_MIN).roundToInt().coerceAtLeast(1)

        return AStarRouteResult(
            originName = startName,
            destinationName = destName,
            pathPoints = pathPoints,
            waypoints = listOf(
                WalkNode("start", startLat, startLng, startName),
                WalkNode("dest", destLat, destLng, destName)
            ),
            steps = listOf(
                RouteStep(
                    instruction = "Walk directly towards $destName for ${distance.roundToInt()} m",
                    distanceMeters = distance,
                    streetName = "Pedestrian Corridor",
                    isPedestrianOnly = true
                ),
                RouteStep(
                    instruction = "Arrive at $destName",
                    distanceMeters = 0.0,
                    streetName = destName,
                    isPedestrianOnly = true
                )
            ),
            totalDistanceMeters = distance,
            estimatedWalkingMinutes = minutes
        )
    }
}
