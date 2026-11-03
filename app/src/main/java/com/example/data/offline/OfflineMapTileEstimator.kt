package com.example.data.offline

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class TileEstimateResult(
    val tileCount: Int,
    val estimatedSizeMB: Double,
    val estimatedDownloadSeconds: Int,
    val formattedSize: String,
    val formattedTime: String
)

/**
 * Utility for estimating offline map tile download sizes, durations, and 30-day stale age checks.
 *
 * Estimation Formulas:
 * - Average vector map tile size = 25 KB (0.025 MB).
 * - Average typical 4G / Wi-Fi download throughput = 1.5 MB/sec.
 * - Estimated MB = tileCount * 0.025 MB.
 * - Estimated Seconds = ceil(estimatedMB / 1.5 MB/s).
 * - Stale Threshold = 30 days (30 * 24 * 3600 * 1000 ms).
 */
object OfflineMapTileEstimator {

    const val AVERAGE_TILE_SIZE_MB = 0.025 // 25 KB average tile size
    const val AVERAGE_DOWNLOAD_SPEED_MBPS = 1.5 // 1.5 MB/sec typical download connection
    const val STALE_THRESHOLD_MS = 30L * 24 * 3600 * 1000L // 30 days

    fun estimateTilePack(
        minZoom: Int = 12,
        maxZoom: Int = 16,
        boundingTileWidth: Int = 5,
        boundingTileHeight: Int = 5
    ): TileEstimateResult {
        var totalTiles = 0
        val baseTiles = max(1, boundingTileWidth * boundingTileHeight)

        for (z in minZoom..maxZoom) {
            val zoomFactor = 1 shl (z - minZoom)
            totalTiles += baseTiles * zoomFactor
        }

        val estimatedMB = totalTiles * AVERAGE_TILE_SIZE_MB
        val estimatedSecs = max(1, ceil(estimatedMB / AVERAGE_DOWNLOAD_SPEED_MBPS).toInt())

        val formattedSize = String.format(Locale.US, "%.1f MB", estimatedMB)
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
            formattedTime = formattedTime
        )
    }

    /**
     * Determines whether a downloaded map region or pack is stale (older than 30 days).
     */
    fun isPackStale(downloadTimestamp: Long, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (downloadTimestamp <= 0) return true
        return (currentTimeMs - downloadTimestamp) >= STALE_THRESHOLD_MS
    }

    /**
     * Formats the download timestamp into a human-readable date (e.g. "Nov 1, 2026").
     */
    fun formatDownloadDate(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0) return "Unknown"
        val formatter = SimpleDateFormat("MMM d, yyyy", locale)
        return formatter.format(Date(timestamp))
    }
}
