package com.example.data.offline

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

data class TileEstimateResult(
    val tileCount: Long,
    val estimatedSizeMB: Double,
    val estimatedDownloadSeconds: Int,
    val formattedSize: String,
    val formattedTime: String,
    val isValid: Boolean = true,
    val isCapped: Boolean = false
)

/**
 * Corrected 2D Web Mercator Tile Estimator.
 *
 * In a 2D square tiled map system, tile bounds (minX, maxX, minY, maxY) are calculated
 * independently at every zoom level to accurately count only tiles intersecting the selected bounding box.
 *
 * Estimation Formulas:
 * - Independent Tile Bounds at Zoom z: (maxX - minX + 1) * (maxY - minY + 1).
 * - Average vector map tile size = 35 KB (0.035 MB).
 * - Download Throughput = 1.5 MB/sec typical connection estimate.
 * - Estimated MB = totalTiles * 0.035 MB.
 * - Estimated Seconds = ceil(estimatedMB / 1.5 MB/s).
 */
object OfflineMapTileEstimator {

    const val AVERAGE_VECTOR_TILE_SIZE_MB = 0.035 // 35 KB average vector tile
    const val AVERAGE_DOWNLOAD_SPEED_MBPS = 1.5 // 1.5 MB/sec typical download throughput estimate
    const val STALE_THRESHOLD_MS = 30L * 24 * 3600 * 1000L // 30 days
    const val MAX_SAFE_TILE_CAP = 1_000_000L

    /**
     * Estimates total tile count, download size, and duration for a geographic bounding box
     * by calculating actual Web Mercator tile bounds independently at every zoom level.
     */
    fun estimateTilePackForBounds(
        bounds: GeoBoundingBox,
        minZoom: Int = 12,
        maxZoom: Int = 16
    ): TileEstimateResult {
        if (!validateZoomRange(minZoom, maxZoom) || !bounds.isValid) {
            return invalidEstimateResult()
        }

        // Antimeridian crossing check (minLng > maxLng) -> reject as invalid
        if (bounds.minLng > bounds.maxLng) {
            return invalidEstimateResult()
        }

        var totalTiles = 0L
        var isCapped = false

        for (z in minZoom..maxZoom) {
            val minX = lngToTileX(bounds.minLng, z)
            val maxX = lngToTileX(bounds.maxLng, z)
            val minY = latToTileY(bounds.maxLat, z) // Max lat maps to smaller Y tile index
            val maxY = latToTileY(bounds.minLat, z)

            val tileWidth = max(1L, (maxX - minX + 1).toLong())
            val tileHeight = max(1L, (maxY - minY + 1).toLong())
            val tilesAtZoom = tileWidth * tileHeight

            totalTiles += tilesAtZoom
            if (totalTiles >= MAX_SAFE_TILE_CAP) {
                totalTiles = MAX_SAFE_TILE_CAP
                isCapped = true
                break
            }
        }

        val estimatedMB = totalTiles * AVERAGE_VECTOR_TILE_SIZE_MB
        val estimatedSecs = max(1, ceil(estimatedMB / AVERAGE_DOWNLOAD_SPEED_MBPS).toInt())

        val formattedSize = if (isCapped) {
            String.format(Locale.US, "Est. >%.1f MB (Capped)", estimatedMB)
        } else {
            String.format(Locale.US, "Est. %.1f MB", estimatedMB)
        }

        val formattedTime = if (estimatedSecs < 60) {
            "~$estimatedSecs sec"
        } else {
            "~${ceil(estimatedSecs / 60.0).toInt()} min"
        }

        return TileEstimateResult(
            tileCount = totalTiles,
            estimatedSizeMB = estimatedMB,
            estimatedDownloadSeconds = estimatedSecs,
            formattedSize = formattedSize,
            formattedTime = formattedTime,
            isValid = true,
            isCapped = isCapped
        )
    }

    /**
     * Estimates total tile count given base tile count at minZoom and zoom range.
     * Uses exact 4x area scaling per zoom level increase.
     */
    fun estimateTilePack(
        minZoom: Int = 12,
        maxZoom: Int = 16,
        boundingTileWidth: Int = 5,
        boundingTileHeight: Int = 5
    ): TileEstimateResult {
        if (!validateZoomRange(minZoom, maxZoom)) {
            return invalidEstimateResult()
        }

        val baseTiles = max(1L, (boundingTileWidth.toLong() * boundingTileHeight.toLong()))
        return estimateTilePackInternal(minZoom, maxZoom, baseTiles)
    }

    private fun estimateTilePackInternal(
        minZoom: Int,
        maxZoom: Int,
        baseTiles: Long
    ): TileEstimateResult {
        var totalTiles = 0L
        var isCapped = false

        for (z in minZoom..maxZoom) {
            val zoomDelta = z - minZoom
            val zoomMultiplier = if (zoomDelta < 15) (1L shl (2 * zoomDelta)) else MAX_SAFE_TILE_CAP
            val tilesAtZoom = baseTiles * zoomMultiplier

            totalTiles += tilesAtZoom
            if (totalTiles >= MAX_SAFE_TILE_CAP) {
                totalTiles = MAX_SAFE_TILE_CAP
                isCapped = true
                break
            }
        }

        val estimatedMB = totalTiles * AVERAGE_VECTOR_TILE_SIZE_MB
        val estimatedSecs = max(1, ceil(estimatedMB / AVERAGE_DOWNLOAD_SPEED_MBPS).toInt())

        val formattedSize = if (isCapped) {
            String.format(Locale.US, "Est. >%.1f MB (Capped)", estimatedMB)
        } else {
            String.format(Locale.US, "Est. %.1f MB", estimatedMB)
        }

        val formattedTime = if (estimatedSecs < 60) {
            "~$estimatedSecs sec"
        } else {
            "~${ceil(estimatedSecs / 60.0).toInt()} min"
        }

        return TileEstimateResult(
            tileCount = totalTiles,
            estimatedSizeMB = estimatedMB,
            estimatedDownloadSeconds = estimatedSecs,
            formattedSize = formattedSize,
            formattedTime = formattedTime,
            isValid = true,
            isCapped = isCapped
        )
    }

    /**
     * Estimates a tile pack for a circular coverage area — the OfflineMapPack model —
     * by deriving the bounding box of the configured radius around the pack center and
     * delegating to [estimateTilePackForBounds]. Uses the pack's actual configured
     * coverage instead of generic default tile dimensions.
     *
     * Degree deltas use the local equirectangular approximation:
     *  1° lat ≈ 111_320 m; 1° lng ≈ 111_320 m × cos(centerLat).
     */
    fun estimateTilePackForRadius(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Int,
        minZoom: Int = 12,
        maxZoom: Int = 16
    ): TileEstimateResult {
        if (!centerLat.isFinite() || !centerLng.isFinite() ||
            centerLat !in -90.0..90.0 || centerLng !in -180.0..180.0 ||
            radiusMeters <= 0) {
            return invalidEstimateResult()
        }

        val latDelta = radiusMeters / 111_320.0
        val lngDelta = radiusMeters / (111_320.0 * cos(Math.toRadians(centerLat)))

        val bounds = GeoBoundingBox(
            minLat = (centerLat - latDelta).coerceIn(-90.0, 90.0),
            minLng = (centerLng - lngDelta).coerceIn(-180.0, 180.0),
            maxLat = (centerLat + latDelta).coerceIn(-90.0, 90.0),
            maxLng = (centerLng + lngDelta).coerceIn(-180.0, 180.0)
        )
        return estimateTilePackForBounds(bounds, minZoom, maxZoom)
    }

    fun validateZoomRange(minZoom: Int, maxZoom: Int): Boolean {
        if (minZoom < 0 || maxZoom > 20) return false
        if (minZoom > maxZoom) return false
        return true
    }

    private fun invalidEstimateResult(): TileEstimateResult {
        return TileEstimateResult(
            tileCount = 0L,
            estimatedSizeMB = 0.0,
            estimatedDownloadSeconds = 0,
            formattedSize = "Est. 0 MB",
            formattedTime = "~0 sec",
            isValid = false,
            isCapped = false
        )
    }

    fun lngToTileX(lng: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val tileX = ((lng + 180.0) / 360.0 * n).toInt()
        return tileX.coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val rad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val n = 1 shl zoom
        val tileY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * n).toInt()
        return tileY.coerceIn(0, n - 1)
    }

    fun isPackStale(downloadTimestamp: Long, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (downloadTimestamp <= 0) return true
        return (currentTimeMs - downloadTimestamp) >= STALE_THRESHOLD_MS
    }

    fun formatDownloadDate(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0) return "Unknown"
        val formatter = SimpleDateFormat("MMM d, yyyy", locale)
        return formatter.format(Date(timestamp))
    }
}
