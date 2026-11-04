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
    val isValid: Boolean = true
)

/**
 * Corrected 2D Web Mercator Tile Estimator.
 *
 * In a 2D square tiled map system, increasing zoom level by +1 quadruples the number of tiles (4x area scaling),
 * as each tile at zoom Z splits into a 2x2 grid of 4 sub-tiles at zoom Z+1.
 *
 * Estimation Formulas:
 * - Base Tile Grid at minZoom: tileWidth * tileHeight.
 * - Zoom Level Scaling: baseTiles * 4^(z - minZoom).
 * - Average vector map tile size = 35 KB (0.035 MB).
 * - Download Throughput = 1.5 MB/sec typical connection.
 * - Estimated MB = totalTiles * 0.035 MB.
 * - Estimated Seconds = ceil(estimatedMB / 1.5 MB/s).
 */
object OfflineMapTileEstimator {

    const val AVERAGE_VECTOR_TILE_SIZE_MB = 0.035 // 35 KB average vector tile
    const val AVERAGE_DOWNLOAD_SPEED_MBPS = 1.5 // 1.5 MB/sec typical download throughput
    const val STALE_THRESHOLD_MS = 30L * 24 * 3600 * 1000L // 30 days
    const val MAX_SAFE_TILE_CAP = 1_000_000L

    /**
     * Estimates total tile count, download size, and duration for a geographic bounding box.
     */
    fun estimateTilePackForBounds(
        bounds: GeoBoundingBox,
        minZoom: Int = 12,
        maxZoom: Int = 16
    ): TileEstimateResult {
        if (!validateZoomRange(minZoom, maxZoom) || !bounds.isValid) {
            return invalidEstimateResult()
        }

        val minX = lngToTileX(bounds.minLng, minZoom)
        val maxX = lngToTileX(bounds.maxLng, minZoom)
        val minY = latToTileY(bounds.maxLat, minZoom) // Max lat maps to smaller Y tile
        val maxY = latToTileY(bounds.minLat, minZoom)

        val tileWidth = max(1L, (maxX - minX + 1).toLong())
        val tileHeight = max(1L, (maxY - minY + 1).toLong())
        val baseTiles = tileWidth * tileHeight

        return estimateTilePackInternal(minZoom, maxZoom, baseTiles)
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

        for (z in minZoom..maxZoom) {
            val zoomDelta = z - minZoom
            // 4^zoomDelta == 1L shl (2 * zoomDelta)
            val zoomMultiplier = if (zoomDelta < 15) (1L shl (2 * zoomDelta)) else MAX_SAFE_TILE_CAP
            val tilesAtZoom = baseTiles * zoomMultiplier

            totalTiles += tilesAtZoom
            if (totalTiles >= MAX_SAFE_TILE_CAP) {
                totalTiles = MAX_SAFE_TILE_CAP
                break
            }
        }

        val estimatedMB = totalTiles * AVERAGE_VECTOR_TILE_SIZE_MB
        val estimatedSecs = max(1, ceil(estimatedMB / AVERAGE_DOWNLOAD_SPEED_MBPS).toInt())

        val formattedSize = String.format(Locale.US, "Est. %.1f MB", estimatedMB)
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
            isValid = true
        )
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
            isValid = false
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
