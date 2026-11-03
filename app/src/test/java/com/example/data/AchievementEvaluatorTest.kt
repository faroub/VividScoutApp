package com.example.data

import com.example.data.local.AchievementEntity
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.VarietyAchievementCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEvaluatorTest {

    private fun createSpot(
        id: String,
        category: String,
        locationName: String = "Lisbon",
        tagsRaw: String = ""
    ): ScoutSpotEntity {
        return ScoutSpotEntity(
            id = id,
            title = "Spot $id",
            description = "Description",
            category = category,
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = "",
            badgeLabel = "Local",
            statusBadge = "Scout Spot",
            tagsRaw = tagsRaw,
            locationName = locationName,
            isDiscovered = true
        )
    }

    @Test
    fun calculateHeritageVariety_countsEachCategoryOnlyOnce() {
        // 5 spots in total, but only 2 distinct categories ("history" and "sights")
        val duplicateCategorySpots = listOf(
            createSpot("s1", "history", "Alfama"),
            createSpot("s2", "history", "Chiado"),
            createSpot("s3", "history", "Baixa"),
            createSpot("s4", "sights", "Belém"),
            createSpot("s5", "sights", "Graça")
        )

        val progress = VarietyAchievementCalculator.calculateHeritageVariety(duplicateCategorySpots)

        assertEquals("5 spots across 2 categories must count as 2 heritage types", 2, progress.currentCount)
        assertEquals("2 of 5 heritage types", progress.progressLabel)
        assertFalse("2 of 5 should not be unlocked", progress.isUnlocked)
    }

    @Test
    fun calculateDistrictVariety_countsEachDistrictOnlyOnce() {
        // 4 spots in total, but only 2 distinct districts ("Alfama" and "Belém")
        val duplicateDistrictSpots = listOf(
            createSpot("s1", "history", "Alfama"),
            createSpot("s2", "sights", "Alfama"),
            createSpot("s3", "eats", "Alfama"),
            createSpot("s4", "sights", "Belém")
        )

        val progress = VarietyAchievementCalculator.calculateDistrictVariety(duplicateDistrictSpots)

        assertEquals("4 spots in 2 districts must count as 2 districts", 2, progress.currentCount)
        assertEquals("2 of 3 districts", progress.progressLabel)
        assertFalse("2 of 3 should not be unlocked", progress.isUnlocked)
    }

    @Test
    fun calculateCountryVariety_countsEachCountryOnlyOnce() {
        // Spots in Lisbon (Portugal) and Dresden (Germany)
        val multiCountrySpots = listOf(
            createSpot("s1", "history", "Alfama, Lisbon", tagsRaw = "country=Portugal"),
            createSpot("s2", "history", "Chiado, Lisbon", tagsRaw = "country=Portugal"),
            createSpot("s3", "history", "Neumarkt, Dresden", tagsRaw = "country=Germany")
        )

        val progress = VarietyAchievementCalculator.calculateCountryVariety(multiCountrySpots)

        assertEquals("Spots in Portugal and Germany must count as 2 countries", 2, progress.currentCount)
        assertEquals("2 of 2 countries", progress.progressLabel)
        assertTrue("2 of 2 countries should be unlocked", progress.isUnlocked)
    }

    @Test
    fun testAchievementEntityProgressPercentage() {
        val achievement = AchievementEntity(
            id = "heritage_variety_5",
            title = "Heritage Variety",
            description = "Visited five different heritage types",
            category = "sights",
            tierLevel = 1,
            currentProgress = 3,
            targetGoal = 5,
            isUnlocked = false
        )

        assertEquals(0.6f, achievement.progressPercentage, 0.01f)
        assertFalse(achievement.isUnlocked)
    }
}
