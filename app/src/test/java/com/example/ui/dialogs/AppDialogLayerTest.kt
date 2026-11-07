package com.example.ui.dialogs

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDialogLayerTest {

    @Test
    fun resolveClearCacheCounts_typicalCounts_subtractsPermanentAndDraftSpots() {
        val counts = resolveClearCacheCounts(
            allSpotsCount = 100,
            savedCount = 30,
            discoveredCount = 20,
            offlineDraftsCount = 5
        )
        assertEquals(45, counts.transientCount)
        assertEquals(30, counts.savedCount)
        assertEquals(20, counts.discoveredCount)
    }

    @Test
    fun resolveClearCacheCounts_emptyDatabase_reportsZeroCounts() {
        val counts = resolveClearCacheCounts(
            allSpotsCount = 0,
            savedCount = 0,
            discoveredCount = 0,
            offlineDraftsCount = 0
        )
        assertEquals(0, counts.transientCount)
        assertEquals(0, counts.savedCount)
        assertEquals(0, counts.discoveredCount)
    }

    @Test
    fun resolveClearCacheCounts_permanentCountsExceedAllSpots_capsPermanentAtAllSpots() {
        val counts = resolveClearCacheCounts(
            allSpotsCount = 10,
            savedCount = 30,
            discoveredCount = 20,
            offlineDraftsCount = 0
        )
        assertEquals(
            "Overlapping saved/discovered sets must not drive transient count negative",
            0,
            counts.transientCount
        )
        assertEquals(30, counts.savedCount)
        assertEquals(20, counts.discoveredCount)
    }

    @Test
    fun resolveClearCacheCounts_draftsOvershootTransient_floorsAtZero() {
        val counts = resolveClearCacheCounts(
            allSpotsCount = 40,
            savedCount = 10,
            discoveredCount = 10,
            offlineDraftsCount = 30
        )
        assertEquals(0, counts.transientCount)
    }
}
