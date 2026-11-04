package com.example.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OfflineMapTileEstimatorTest {

    @Test
    fun estimateTilePack_singleZoomLevel_matchesIndependentGroundTruth() {
        // Base grid = 2 x 3 = 6 tiles at zoom 14 only
        val result = OfflineMapTileEstimator.estimateTilePack(
            minZoom = 14,
            maxZoom = 14,
            boundingTileWidth = 2,
            boundingTileHeight = 3
        )

        // Hand-calculated expected value: 2 * 3 = 6 tiles
        assertEquals(6L, result.tileCount)
        assertTrue(result.isValid)
    }

    @Test
    fun estimateTilePack_multipleZoomLevels_quadruplesTilesPerZoomIncrease() {
        // Base grid = 1 x 1 = 1 tile at minZoom 12 through maxZoom 14
        // Zoom 12: 1 tile
        // Zoom 13: 4 tiles (1 * 4^1)
        // Zoom 14: 16 tiles (1 * 4^2)
        // Expected Total = 1 + 4 + 16 = 21 tiles
        val result = OfflineMapTileEstimator.estimateTilePack(
            minZoom = 12,
            maxZoom = 14,
            boundingTileWidth = 1,
            boundingTileHeight = 1
        )

        // Hand-calculated expected ground truth constant: 21 tiles
        assertEquals(21L, result.tileCount)
    }

    @Test
    fun estimateTilePack_eachAdditionalZoomLevel_scalesQuadratically() {
        // 1 tile base at zoom 12
        val zoom12Only = OfflineMapTileEstimator.estimateTilePack(12, 12, 1, 1).tileCount
        assertEquals(1L, zoom12Only) // 1

        val zoom12To13 = OfflineMapTileEstimator.estimateTilePack(12, 13, 1, 1).tileCount
        assertEquals(5L, zoom12To13) // 1 + 4 = 5

        val zoom12To14 = OfflineMapTileEstimator.estimateTilePack(12, 14, 1, 1).tileCount
        assertEquals(21L, zoom12To14) // 1 + 4 + 16 = 21

        val zoom12To15 = OfflineMapTileEstimator.estimateTilePack(12, 15, 1, 1).tileCount
        assertEquals(85L, zoom12To15) // 1 + 4 + 16 + 64 = 85
    }

    @Test
    fun estimateTilePack_differentBoundingAreas_scalesBaseTilesAccurately() {
        // Base grid = 2 x 4 = 8 tiles at zoom 12..13
        // Zoom 12: 8 tiles
        // Zoom 13: 8 * 4 = 32 tiles
        // Expected Total = 8 + 32 = 40 tiles
        val result = OfflineMapTileEstimator.estimateTilePack(
            minZoom = 12,
            maxZoom = 13,
            boundingTileWidth = 2,
            boundingTileHeight = 4
        )

        // Hand-calculated expected ground truth constant: 40 tiles
        assertEquals(40L, result.tileCount)
    }

    @Test
    fun estimateTilePack_invalidZoomRanges_returnsInvalidEstimateResult() {
        // Inverted range (minZoom > maxZoom)
        val inverted = OfflineMapTileEstimator.estimateTilePack(minZoom = 16, maxZoom = 12)
        assertFalse("Inverted zoom range must be marked invalid", inverted.isValid)
        assertEquals(0L, inverted.tileCount)

        // Out of bounds minZoom (-1)
        val negativeZoom = OfflineMapTileEstimator.estimateTilePack(minZoom = -1, maxZoom = 10)
        assertFalse("Negative zoom must be marked invalid", negativeZoom.isValid)
        assertEquals(0L, negativeZoom.tileCount)
    }

    @Test
    fun estimateTilePack_largeRegionsAndOverflowBoundaries_capsSafelyWithoutOverflow() {
        // Extreme zoom range 0..20
        val result = OfflineMapTileEstimator.estimateTilePack(
            minZoom = 0,
            maxZoom = 20,
            boundingTileWidth = 10,
            boundingTileHeight = 10
        )

        assertTrue(result.isValid)
        assertEquals("Large regions must cap safely at MAX_SAFE_TILE_CAP without overflow",
            OfflineMapTileEstimator.MAX_SAFE_TILE_CAP, result.tileCount)
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
