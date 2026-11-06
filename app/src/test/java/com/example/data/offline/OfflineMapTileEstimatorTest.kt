package com.example.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OfflineMapTileEstimatorTest {

    @Test
    fun webMercatorXyz_knownCoordinates_calculatesExactTileCoordinates() {
        // Standard Web Mercator XYZ test value:
        // Lisbon 38.7112° N, -9.1423° W at zoom 12 -> Tile X = 1943, Tile Y = 1569
        val tileX = OfflineMapTileEstimator.lngToTileX(-9.1423, 12)
        val tileY = OfflineMapTileEstimator.latToTileY(38.7112, 12)

        assertEquals("Lisbon longitude -9.1423 at zoom 12 maps to tile X = 1943", 1943, tileX)
        assertEquals("Lisbon latitude 38.7112 at zoom 12 maps to tile Y = 1569", 1569, tileY)
    }

    @Test
    fun estimateTilePackForBounds_tinySelectionInOneTile_countsOneTilePerZoom() {
        // Very tight 1m x 1m bounding box in central Lisbon (contained in single tile at zoom 12..16)
        val tinyBounds = GeoBoundingBox(
            minLat = 38.71120,
            minLng = -9.14230,
            maxLat = 38.71121,
            maxLng = -9.14229
        )

        val result = OfflineMapTileEstimator.estimateTilePackForBounds(
            bounds = tinyBounds,
            minZoom = 12,
            maxZoom = 16
        )

        assertTrue(result.isValid)
        assertFalse(result.isCapped)

        // Hand-calculated expected value: 1 tile at each zoom 12, 13, 14, 15, 16 = 5 total tiles
        assertEquals("Tiny 1m selection contained in 1 tile at zoom 12..16 must equal 5 total tiles", 5L, result.tileCount)
    }

    @Test
    fun estimateTilePackForBounds_selectionCrossingHorizontalTileBoundary_countsBothTiles() {
        // Bounding box straddling longitude -9.140625 (boundary between X=1943 and X=1944 at zoom 12)
        val horizontalBoundaryBounds = GeoBoundingBox(
            minLat = 38.7110,
            minLng = -9.1410,
            maxLat = 38.7114,
            maxLng = -9.1390
        )

        val minX = OfflineMapTileEstimator.lngToTileX(horizontalBoundaryBounds.minLng, 12)
        val maxX = OfflineMapTileEstimator.lngToTileX(horizontalBoundaryBounds.maxLng, 12)

        assertEquals(1943, minX)
        assertEquals(1944, maxX) // Crosses horizontal boundary

        val result = OfflineMapTileEstimator.estimateTilePackForBounds(
            bounds = horizontalBoundaryBounds,
            minZoom = 12,
            maxZoom = 12
        )

        assertEquals("Selection crossing 1 horizontal tile boundary at zoom 12 must count 2 tiles", 2L, result.tileCount)
    }

    @Test
    fun estimateTilePackForBounds_selectionCrossingVerticalTileBoundary_countsBothTiles() {
        // Bounding box straddling latitude 38.685° N (boundary between Y=1569 and Y=1570 at zoom 12)
        val verticalBoundaryBounds = GeoBoundingBox(
            minLat = 38.6700,
            minLng = -9.1423,
            maxLat = 38.7000,
            maxLng = -9.1420
        )

        val minY = OfflineMapTileEstimator.latToTileY(verticalBoundaryBounds.maxLat, 12)
        val maxY = OfflineMapTileEstimator.latToTileY(verticalBoundaryBounds.minLat, 12)

        assertEquals(1569, minY)
        assertEquals(1570, maxY) // Crosses vertical boundary

        val result = OfflineMapTileEstimator.estimateTilePackForBounds(
            bounds = verticalBoundaryBounds,
            minZoom = 12,
            maxZoom = 12
        )

        assertEquals("Selection crossing 1 vertical tile boundary at zoom 12 must count 2 tiles", 2L, result.tileCount)
    }

    @Test
    fun estimateTilePackForBounds_selectionCrossingBothBoundaries_countsFourTiles() {
        // Bounding box crossing both horizontal (-9.140625°) and vertical (38.685°) boundaries at zoom 12
        val crossBounds = GeoBoundingBox(
            minLat = 38.6700,
            minLng = -9.1410,
            maxLat = 38.7000,
            maxLng = -9.1390
        )

        val result = OfflineMapTileEstimator.estimateTilePackForBounds(
            bounds = crossBounds,
            minZoom = 12,
            maxZoom = 12
        )

        assertEquals("Selection crossing both boundaries at zoom 12 must count 2x2 = 4 tiles", 4L, result.tileCount)
    }

    @Test
    fun estimateTilePackForBounds_antimeridianCrossing_isRejectedAsInvalid() {
        // Antimeridian crossing (minLng = 179.0 > maxLng = -179.0)
        val antimeridianBounds = GeoBoundingBox(
            minLat = 20.0,
            minLng = 179.0,
            maxLat = 22.0,
            maxLng = -179.0
        )

        val result = OfflineMapTileEstimator.estimateTilePackForBounds(
            bounds = antimeridianBounds,
            minZoom = 12,
            maxZoom = 14
        )

        assertFalse("Antimeridian crossing bounds must be rejected as invalid", result.isValid)
        assertEquals(0L, result.tileCount)
    }

    @Test
    fun estimateTilePackForBounds_overflowAndCappedEstimates_indicatesCappedStateInFormattedSize() {
        // Large region covering Europe
        val largeRegionBounds = GeoBoundingBox(
            minLat = 35.0,
            minLng = -10.0,
            maxLat = 60.0,
            maxLng = 30.0
        )

        val result = OfflineMapTileEstimator.estimateTilePackForBounds(
            bounds = largeRegionBounds,
            minZoom = 12,
            maxZoom = 18
        )

        assertTrue(result.isValid)
        assertTrue("Large region exceeding 1M tiles MUST be marked as isCapped = true", result.isCapped)
        assertEquals(OfflineMapTileEstimator.MAX_SAFE_TILE_CAP, result.tileCount)
        assertTrue("Formatted size MUST explicitly label capped results", result.formattedSize.contains("Capped"))
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

    @Test
    fun estimateTilePackForRadius_twoCitiesWithSameRadius_produceDifferentEstimates() {
        // The two preset packs from OfflineMapManager: both 3500 m radius, but Berlin's
        // longitude degree is compressed at its higher latitude, so its bounding box
        // covers more tiles than Lisbon's. The generic 5x5 default showed identical
        // estimates for both.
        val lisbon = OfflineMapTileEstimator.estimateTilePackForRadius(
            centerLat = 38.7112, centerLng = -9.1423, radiusMeters = 3500
        )
        val berlin = OfflineMapTileEstimator.estimateTilePackForRadius(
            centerLat = 52.5200, centerLng = 13.4050, radiusMeters = 3500
        )

        assertTrue(lisbon.isValid)
        assertTrue(berlin.isValid)
        assertTrue(
            "Lisbon and Berlin packs with equal radii must produce different tile counts, " +
                "got lisbon=${lisbon.tileCount} berlin=${berlin.tileCount}",
            lisbon.tileCount != berlin.tileCount
        )
    }

    @Test
    fun estimateTilePackForRadius_identicalPacks_produceIdenticalEstimates() {
        val first = OfflineMapTileEstimator.estimateTilePackForRadius(
            centerLat = 48.8566, centerLng = 2.3522, radiusMeters = 3500
        )
        val second = OfflineMapTileEstimator.estimateTilePackForRadius(
            centerLat = 48.8566, centerLng = 2.3522, radiusMeters = 3500
        )

        assertEquals(first.tileCount, second.tileCount)
        assertEquals(first.formattedSize, second.formattedSize)
    }

    @Test
    fun estimateTilePackForRadius_invalidCenters_returnInvalidEstimates() {
        assertFalse("NaN center latitude", OfflineMapTileEstimator.estimateTilePackForRadius(
            Double.NaN, -9.1423, 3500).isValid)
        assertFalse("Out-of-range center latitude", OfflineMapTileEstimator.estimateTilePackForRadius(
            91.0, -9.1423, 3500).isValid)
        assertFalse("NaN center longitude", OfflineMapTileEstimator.estimateTilePackForRadius(
            38.7112, Double.NaN, 3500).isValid)
    }

    @Test
    fun estimateTilePackForRadius_nonPositiveRadius_returnsInvalidEstimate() {
        val zero = OfflineMapTileEstimator.estimateTilePackForRadius(38.7112, -9.1423, 0)
        val negative = OfflineMapTileEstimator.estimateTilePackForRadius(38.7112, -9.1423, -1000)
        assertFalse(zero.isValid)
        assertFalse(negative.isValid)
        assertEquals(0L, zero.tileCount)
    }

    @Test
    fun estimateTilePackForRadius_hugeRadius_isCappedAndLabeled() {
        // A 3000 km radius spans a continent: must hit the 1M-tile safety cap.
        val huge = OfflineMapTileEstimator.estimateTilePackForRadius(38.7112, -9.1423, 3_000_000)
        assertTrue(huge.isValid)
        assertTrue(huge.isCapped)
        assertEquals(OfflineMapTileEstimator.MAX_SAFE_TILE_CAP, huge.tileCount)
        assertTrue("Capped estimates must be labeled", huge.formattedSize.contains("Capped"))
    }

    @Test
    fun estimateTilePackForRadius_zoomRangeValidation_rejectsInvalidRanges() {
        assertFalse(OfflineMapTileEstimator.estimateTilePackForRadius(
            38.7112, -9.1423, 3500, minZoom = 16, maxZoom = 12).isValid)
        assertFalse(OfflineMapTileEstimator.estimateTilePackForRadius(
            38.7112, -9.1423, 3500, minZoom = 12, maxZoom = 21).isValid)
    }

    @Test
    fun estimateTilePackForRadius_antimeridianCenter_staysValid() {
        // Coercion must keep a date-line pack's box within valid longitude bounds
        // instead of producing an invalid antimeridian-crossing box.
        val dateLine = OfflineMapTileEstimator.estimateTilePackForRadius(
            0.0, 179.99, 3500
        )
        assertTrue(dateLine.isValid)
        assertFalse(dateLine.isCapped)
    }

    @Test
    fun offlineMapPack_displaySize_prefersActualStoredBytes() {
        val downloaded = OfflineMapPack(
            id = "test_pack", cityName = "Test", country = "TC",
            lat = 0.0, lng = 0.0,
            estimatedSizeBytes = 4_200_000L,
            actualSizeBytes = 2_100_000L,
            isDownloaded = true
        )
        val pending = downloaded.copy(isDownloaded = false, actualSizeBytes = 0L)

        assertEquals("2.0 MB", downloaded.displaySize)
        assertEquals("4.0 MB", pending.displaySize)
    }
}
