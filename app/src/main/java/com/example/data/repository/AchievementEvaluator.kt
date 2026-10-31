package com.example.data.repository

import com.example.data.local.AchievementDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility evaluating discovered spots in Scout Lisbon and updating achievement badge progress in Room DB.
 */
class AchievementEvaluator(
    private val scoutDao: ScoutDao,
    private val achievementDao: AchievementDao
) {

    suspend fun evaluateAllAchievements() = withContext(Dispatchers.IO) {
        val discoveredSpots = scoutDao.getDiscoveredSpotsSync()
        val allSpots = scoutDao.getAllSpotsSync()

        val sightsCount = discoveredSpots.count { it.category == "sights" }
        val eatsCount = discoveredSpots.count { it.category == "eats" }
        val historyCount = discoveredSpots.count { it.category == "history" }
        val shortcutCount = discoveredSpots.count { it.category == "shortcut" }
        val offlineDraftCount = allSpots.count { it.isOfflineDraft || it.isDiscovered }

        updateAchievement("sights_explorer", sightsCount, 3)
        updateAchievement("foodie_scout", eatsCount, 3)
        updateAchievement("history_buff", historyCount, 2)
        updateAchievement("shortcut_master", shortcutCount, 2)
        updateAchievement("offline_pioneer", offlineDraftCount, 1)
    }

    private suspend fun updateAchievement(id: String, currentCount: Int, goal: Int) {
        val existing = achievementDao.getAchievementById(id) ?: return
        val isNowUnlocked = currentCount >= goal
        val unlockedTimestamp = if (isNowUnlocked && !existing.isUnlocked) {
            System.currentTimeMillis()
        } else if (existing.isUnlocked) {
            existing.unlockedTimestamp
        } else {
            0L
        }

        achievementDao.updateProgress(
            id = id,
            progress = currentCount.coerceAtMost(goal),
            unlocked = isNowUnlocked,
            unlockedTimestamp = unlockedTimestamp
        )
    }
}
