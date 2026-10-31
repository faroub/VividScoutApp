package com.example.util

import com.example.data.local.AchievementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerTierResolverTest {

    @Test
    fun testResolveTierForCategoryStandard() {
        val achievements = emptyList<AchievementEntity>()
        val style = MarkerTierResolver.resolveTierForCategory("sights", achievements)

        assertEquals(MarkerTier.STANDARD, style.tier)
        assertEquals(false, style.isPulsing)
    }

    @Test
    fun testResolveTierForCategoryBronze() {
        val achievements = listOf(
            AchievementEntity(
                id = "sights_explorer",
                title = "Sights Explorer",
                description = "Discover spots",
                category = "sights",
                currentProgress = 1,
                targetGoal = 3,
                isUnlocked = false
            )
        )
        val style = MarkerTierResolver.resolveTierForCategory("sights", achievements)

        assertEquals(MarkerTier.BRONZE, style.tier)
        assertEquals(true, style.isPulsing)
        assertEquals(MarkerTierResolver.ColorBronze, style.auraColor)
    }

    @Test
    fun testResolveTierForCategoryGoldUnlocked() {
        val achievements = listOf(
            AchievementEntity(
                id = "sights_explorer",
                title = "Sights Explorer",
                description = "Discover spots",
                category = "sights",
                currentProgress = 3,
                targetGoal = 3,
                isUnlocked = true
            )
        )
        val style = MarkerTierResolver.resolveTierForCategory("sights", achievements)

        assertEquals(MarkerTier.GOLD, style.tier)
        assertEquals(true, style.isPulsing)
        assertEquals(true, style.showSparkle)
        assertEquals(MarkerTierResolver.ColorGold, style.auraColor)
    }
}
