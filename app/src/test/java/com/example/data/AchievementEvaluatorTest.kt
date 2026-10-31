package com.example.data

import com.example.data.local.AchievementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEvaluatorTest {

    @Test
    fun testAchievementEntityProgressPercentage() {
        val achievement = AchievementEntity(
            id = "sights_explorer",
            title = "Sights Explorer",
            description = "Discover 3 scenic viewpoints",
            category = "sights",
            tierLevel = 1,
            currentProgress = 2,
            targetGoal = 3,
            isUnlocked = false
        )

        assertEquals(0.666f, achievement.progressPercentage, 0.01f)
        assertFalse(achievement.isUnlocked)
    }

    @Test
    fun testAchievementEntityUnlockedProgress() {
        val achievement = AchievementEntity(
            id = "foodie_scout",
            title = "Foodie Scout",
            description = "Sample 3 local bakery tarts",
            category = "eats",
            tierLevel = 1,
            currentProgress = 3,
            targetGoal = 3,
            isUnlocked = true
        )

        assertEquals(1.0f, achievement.progressPercentage, 0.001f)
        assertTrue(achievement.isUnlocked)
    }
}
