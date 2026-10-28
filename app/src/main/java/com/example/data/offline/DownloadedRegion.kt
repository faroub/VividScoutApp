package com.example.data.offline

import android.content.Context
import androidx.annotation.Keep
import com.example.data.local.ScoutSpotEntity
import com.example.engine.ElevationPoint
import com.example.engine.OfflineRouteEngine.haversineMeters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt

@Keep
data class GeoBoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    val centerLat: Double
        get() = (minLat + maxLat) / 2.0

    val centerLng: Double
        get() = (minLng + maxLng) / 2.0

    val widthKm: Double
        get() {
            val d = haversineMeters(centerLat, minLng, centerLat, maxLng)
            return d / 1000.0
        }

    val heightKm: Double
        get() {
            val d = haversineMeters(minLat, centerLng, maxLat, centerLng)
            return d / 1000.0
        }

    val formattedDimensions: String
        get() = String.format(java.util.Locale.US, "%.1f km × %.1f km", widthKm, heightKm)

    fun contains(lat: Double, lng: Double): Boolean {
        return lat in minLat..maxLat && lng in minLng..maxLng
    }
}

@Keep
data class DownloadedRoadSegment(
    val id: String,
    val fromId: String,
    val toId: String,
    val fromLat: Double,
    val fromLng: Double,
    val toLat: Double,
    val toLng: Double,
    val distanceMeters: Double,
    val streetName: String,
    val isPedestrian: Boolean = true,
    val isStairs: Boolean = false
)

@Keep
data class DownloadedRegionPack(
    val id: String,
    val name: String,
    val bounds: GeoBoundingBox,
    val roadSegments: List<DownloadedRoadSegment>,
    val elevationAnchors: List<ElevationPoint>,
    val spotsCount: Int,
    val totalSizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() {
            val mb = totalSizeBytes.toDouble() / (1024 * 1024)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}

/**
 * Storage manager for custom downloaded offline regions.
 * Stores road networks, elevation anchors, and region metadata on local disk.
 */
class OfflineRegionStorage(private val context: Context) {

    private val regionDir: File by lazy {
        File(context.filesDir, "offline_custom_regions").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun getAllRegions(): List<DownloadedRegionPack> {
        val files = regionDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        val regions = mutableListOf<DownloadedRegionPack>()

        for (file in files) {
            try {
                val jsonStr = file.readText()
                val json = JSONObject(jsonStr)

                val id = json.getString("id")
                val name = json.getString("name")
                val minLat = json.getDouble("minLat")
                val minLng = json.getDouble("minLng")
                val maxLat = json.getDouble("maxLat")
                val maxLng = json.getDouble("maxLng")
                val totalSizeBytes = json.optLong("totalSizeBytes", file.length())
                val timestamp = json.optLong("timestamp", file.lastModified())
                val spotsCount = json.optInt("spotsCount", 0)

                val bounds = GeoBoundingBox(minLat, minLng, maxLat, maxLng)

                val segments = mutableListOf<DownloadedRoadSegment>()
                val segArray = json.optJSONArray("roadSegments") ?: JSONArray()
                for (i in 0 until segArray.length()) {
                    val sObj = segArray.getJSONObject(i)
                    segments.add(
                        DownloadedRoadSegment(
                            id = sObj.getString("id"),
                            fromId = sObj.getString("fromId"),
                            toId = sObj.getString("toId"),
                            fromLat = sObj.getDouble("fromLat"),
                            fromLng = sObj.getDouble("fromLng"),
                            toLat = sObj.getDouble("toLat"),
                            toLng = sObj.getDouble("toLng"),
                            distanceMeters = sObj.getDouble("distanceMeters"),
                            streetName = sObj.getString("streetName"),
                            isPedestrian = sObj.optBoolean("isPedestrian", true),
                            isStairs = sObj.optBoolean("isStairs", false)
                        )
                    )
                }

                val elevAnchors = mutableListOf<ElevationPoint>()
                val elevArray = json.optJSONArray("elevationAnchors") ?: JSONArray()
                for (i in 0 until elevArray.length()) {
                    val eObj = elevArray.getJSONObject(i)
                    elevAnchors.add(
                        ElevationPoint(
                            distanceMeters = eObj.getDouble("distanceMeters"),
                            elevationMeters = eObj.getDouble("elevationMeters"),
                            slopeGradePct = eObj.optDouble("slopeGradePct", 0.0)
                        )
                    )
                }

                regions.add(
                    DownloadedRegionPack(
                        id = id,
                        name = name,
                        bounds = bounds,
                        roadSegments = segments,
                        elevationAnchors = elevAnchors,
                        spotsCount = spotsCount,
                        totalSizeBytes = totalSizeBytes,
                        timestamp = timestamp
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("OfflineRegionStorage", "Failed to parse region pack: ${file.name}", e)
            }
        }

        return regions.sortedByDescending { it.timestamp }
    }

    fun saveRegion(pack: DownloadedRegionPack) {
        val file = File(regionDir, "${pack.id}.json")
        val json = JSONObject().apply {
            put("id", pack.id)
            put("name", pack.name)
            put("minLat", pack.bounds.minLat)
            put("minLng", pack.bounds.minLng)
            put("maxLat", pack.bounds.maxLat)
            put("maxLng", pack.bounds.maxLng)
            put("spotsCount", pack.spotsCount)
            put("totalSizeBytes", pack.totalSizeBytes)
            put("timestamp", pack.timestamp)

            val segArray = JSONArray()
            for (seg in pack.roadSegments) {
                val sObj = JSONObject().apply {
                    put("id", seg.id)
                    put("fromId", seg.fromId)
                    put("toId", seg.toId)
                    put("fromLat", seg.fromLat)
                    put("fromLng", seg.fromLng)
                    put("toLat", seg.toLat)
                    put("toLng", seg.toLng)
                    put("distanceMeters", seg.distanceMeters)
                    put("streetName", seg.streetName)
                    put("isPedestrian", seg.isPedestrian)
                    put("isStairs", seg.isStairs)
                }
                segArray.put(sObj)
            }
            put("roadSegments", segArray)

            val elevArray = JSONArray()
            for (elev in pack.elevationAnchors) {
                val eObj = JSONObject().apply {
                    put("distanceMeters", elev.distanceMeters)
                    put("elevationMeters", elev.elevationMeters)
                    put("slopeGradePct", elev.slopeGradePct)
                }
                elevArray.put(eObj)
            }
            put("elevationAnchors", elevArray)
        }

        file.writeText(json.toString(2))
    }

    fun deleteRegion(id: String): Boolean {
        val file = File(regionDir, "$id.json")
        return if (file.exists()) file.delete() else false
    }

    /**
     * Synthesizes a connected vector road segment network within a bounding box.
     * Incorporates grid connections, primary thoroughfares, and pedestrian alleyways.
     */
    fun synthesizeRoadNetworkForBounds(
        bounds: GeoBoundingBox,
        regionName: String,
        cachedSpots: List<ScoutSpotEntity>
    ): Pair<List<DownloadedRoadSegment>, List<ElevationPoint>> {
        val segments = mutableListOf<DownloadedRoadSegment>()
        val elevAnchors = mutableListOf<ElevationPoint>()

        val gridSteps = 4
        val latStep = (bounds.maxLat - bounds.minLat) / gridSteps
        val lngStep = (bounds.maxLng - bounds.minLng) / gridSteps

        // Generate grid intersections
        val gridNodes = Array(gridSteps + 1) { r ->
            Array(gridSteps + 1) { c ->
                val lat = bounds.minLat + (r * latStep)
                val lng = bounds.minLng + (c * lngStep)
                val id = "node_${r}_${c}"
                Triple(id, lat, lng)
            }
        }

        var segIdx = 0

        // Horizontal street segments
        for (r in 0..gridSteps) {
            for (c in 0 until gridSteps) {
                val n1 = gridNodes[r][c]
                val n2 = gridNodes[r][c + 1]
                val dist = haversineMeters(n1.second, n1.third, n2.second, n2.third)
                val streetName = "$regionName Way ${r + 1}"
                segments.add(
                    DownloadedRoadSegment(
                        id = "seg_${segIdx++}",
                        fromId = n1.first,
                        toId = n2.first,
                        fromLat = n1.second,
                        fromLng = n1.third,
                        toLat = n2.second,
                        toLng = n2.third,
                        distanceMeters = dist,
                        streetName = streetName,
                        isPedestrian = r % 2 == 1,
                        isStairs = r == gridSteps / 2
                    )
                )
            }
        }

        // Vertical street segments
        for (c in 0..gridSteps) {
            for (r in 0 until gridSteps) {
                val n1 = gridNodes[r][c]
                val n2 = gridNodes[r + 1][c]
                val dist = haversineMeters(n1.second, n1.third, n2.second, n2.third)
                val streetName = "$regionName Avenue ${('A'.code + c).toChar()}"
                segments.add(
                    DownloadedRoadSegment(
                        id = "seg_${segIdx++}",
                        fromId = n1.first,
                        toId = n2.first,
                        fromLat = n1.second,
                        fromLng = n1.third,
                        toLat = n2.second,
                        toLng = n2.third,
                        distanceMeters = dist,
                        streetName = streetName,
                        isPedestrian = c % 2 == 0,
                        isStairs = false
                    )
                )
            }
        }

        // Connect cached spots within bounds to nearest grid node
        val spotsInBounds = cachedSpots.filter { bounds.contains(it.latitude, it.longitude) }
        spotsInBounds.forEachIndexed { idx, spot ->
            var closestNode = gridNodes[0][0]
            var minDist = Double.MAX_VALUE

            for (r in 0..gridSteps) {
                for (c in 0..gridSteps) {
                    val n = gridNodes[r][c]
                    val d = haversineMeters(spot.latitude, spot.longitude, n.second, n.third)
                    if (d < minDist) {
                        minDist = d
                        closestNode = n
                    }
                }
            }

            segments.add(
                DownloadedRoadSegment(
                    id = "spot_connector_${idx}",
                    fromId = "spot_${spot.id}",
                    toId = closestNode.first,
                    fromLat = spot.latitude,
                    fromLng = spot.longitude,
                    toLat = closestNode.second,
                    toLng = closestNode.third,
                    distanceMeters = minDist,
                    streetName = "Path to ${spot.title}",
                    isPedestrian = true,
                    isStairs = false
                )
            )
        }

        // Elevation anchors across the bounds
        var cumulativeD = 0.0
        for (i in 0..gridSteps) {
            val elev = 25.0 + (i * 12.0)
            elevAnchors.add(
                ElevationPoint(
                    distanceMeters = cumulativeD,
                    elevationMeters = elev,
                    slopeGradePct = 3.5
                )
            )
            cumulativeD += 300.0
        }

        return Pair(segments, elevAnchors)
    }
}
