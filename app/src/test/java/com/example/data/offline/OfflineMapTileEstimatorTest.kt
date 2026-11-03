package com.example.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OfflineMapTileEstimatorTest {

    @Test
    fun estimateTilePack_calculatesSizeAndDownloadTimeAccurately() {
        val estimate = OfflineMapTileEstimator.estimateTilePack(
            minZoom = 12,
            maxZoom = 16,
            boundingTileWidth = 5,
            boundingTileHeight = 5
        )

        assertTrue("Tile count must be positive", estimate.tileCount > 0)

        val expectedSizeMB = estimate.tileCount * OfflineMapTileEstimator.AVERAGE_TILE_SIZE_MB
        assertEquals(expectedSizeMB, estimate.estimatedSizeMB, 0.01)

        val expectedSeconds = Math.max(1, Math.ceil(expectedSizeMB / OfflineMapTileEstimator.AVERAGE_DOWNLOAD_SPEED_MBPS).toInt())
        assertEquals(expectedSeconds, estimate.estimatedDownloadSeconds)
    }

    @Test
    fun isPackStale_packOlderThanThirtyDays_returnsTrue() {
        val now = System.currentTimeMillis()
        val tenDaysOld = now - (10L * 24 * 3600 * 1000L)
        val thirtyFiveDaysOld = now - (35L * 24 * 3600 * 1000L)

        assertFalse("Pack 10 days old should not be stale", OfflineMapTileEstimator.isPackStale(tenDaysOld, now))
        assertTrue("Pack 35 days old must be stale", OfflineMapTileEstimator.isPackStale(thirtyFiveDaysOld, now))
    }

    @Test
    fun formatDownloadDate_formatsHumanReadableDate() {
        val timestamp = 1793491200000L // Nov 1, 2026
        val formatted = OfflineMapTileEstimator.formatDownloadDate(timestamp, Locale.US)
        assertEquals("Nov 1, 2026", formatted)
    }
}
