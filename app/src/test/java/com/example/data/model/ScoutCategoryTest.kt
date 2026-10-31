package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutCategoryTest {

    @Test
    fun normalize_mapsRawCategoryNamesToCanonicalInternalIDs() {
        // Sights
        assertEquals("sights", ScoutCategory.normalize("touristic"))
        assertEquals("sights", ScoutCategory.normalize("sightseeing"))
        assertEquals("sights", ScoutCategory.normalize("viewpoint"))
        assertEquals("sights", ScoutCategory.normalize("sights"))

        // Eats
        assertEquals("eats", ScoutCategory.normalize("restaurant"))
        assertEquals("eats", ScoutCategory.normalize("dining"))
        assertEquals("eats", ScoutCategory.normalize("bakery"))
        assertEquals("eats", ScoutCategory.normalize("cafe"))
        assertEquals("eats", ScoutCategory.normalize("eats"))

        // History
        assertEquals("history", ScoutCategory.normalize("historical"))
        assertEquals("history", ScoutCategory.normalize("monument"))
        assertEquals("history", ScoutCategory.normalize("castle"))
        assertEquals("history", ScoutCategory.normalize("museum"))
        assertEquals("history", ScoutCategory.normalize("history"))

        // Shortcut
        assertEquals("shortcut", ScoutCategory.normalize("gems"))
        assertEquals("shortcut", ScoutCategory.normalize("passage"))
        assertEquals("shortcut", ScoutCategory.normalize("secret"))
        assertEquals("shortcut", ScoutCategory.normalize("shortcut"))

        // All / Unknown
        assertEquals("all", ScoutCategory.normalize("ALL"))
        assertEquals("all", ScoutCategory.normalize("all"))
        assertEquals("sights", ScoutCategory.normalize("unknown_xyz"))
    }

    @Test
    fun matches_evaluatesNormalizedCategoryMatching() {
        // AI spot generated as "restaurant" matches UI filter "eats"
        assertTrue(ScoutCategory.matches(filterCategory = "eats", spotCategory = "restaurant"))

        // AI spot generated as "touristic" matches UI filter "sights"
        assertTrue(ScoutCategory.matches(filterCategory = "sights", spotCategory = "touristic"))

        // AI spot generated as "historical" matches UI filter "history"
        assertTrue(ScoutCategory.matches(filterCategory = "history", spotCategory = "historical"))

        // Any spot matches filter "all" or "ALL"
        assertTrue(ScoutCategory.matches(filterCategory = "all", spotCategory = "restaurant"))
        assertTrue(ScoutCategory.matches(filterCategory = "ALL", spotCategory = "monument"))
    }
}
