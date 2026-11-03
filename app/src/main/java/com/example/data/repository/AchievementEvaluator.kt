package com.example.data.repository

import com.example.data.local.AchievementDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VarietyAchievementProgress(
    val id: String,
    val title: String,
    val description: String,
    val currentCount: Int,
    val goalCount: Int,
    val progressLabel: String
) {
    val isUnlocked: Boolean get() = currentCount >= goalCount
}

object VarietyAchievementCalculator {

    fun calculateHeritageVariety(discoveredSpots: List<ScoutSpotEntity>): VarietyAchievementProgress {
        val distinctCategories = discoveredSpots.map { it.category.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val count = distinctCategories.size
        val goal = 5

        return VarietyAchievementProgress(
            id = "heritage_variety_5",
            title = "Heritage Variety",
            description = "Visited five different heritage types",
            currentCount = count.coerceAtMost(goal),
            goalCount = goal,
            progressLabel = "$count of $goal heritage types"
        )
    }

    fun calculateDistrictVariety(discoveredSpots: List<ScoutSpotEntity>): VarietyAchievementProgress {
        val distinctDistricts = discoveredSpots.map { it.locationName.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val count = distinctDistricts.size
        val goal = 3

        return VarietyAchievementProgress(
            id = "district_variety_3",
            title = "District Explorer",
            description = "Walked in three different districts",
            currentCount = count.coerceAtMost(goal),
            goalCount = goal,
            progressLabel = "$count of $goal districts"
        )
    }

    fun calculateCountryVariety(discoveredSpots: List<ScoutSpotEntity>): VarietyAchievementProgress {
        val distinctCountries = discoveredSpots.map { extractCountry(it).lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val count = distinctCountries.size
        val goal = 2

        return VarietyAchievementProgress(
            id = "country_variety_2",
            title = "Globe Trotter",
            description = "Visited spots in two countries",
            currentCount = count.coerceAtMost(goal),
            goalCount = goal,
            progressLabel = "$count of $goal countries"
        )
    }

    fun extractCountry(spot: ScoutSpotEntity): String {
        val tags = spot.tagsRaw.split("|||")
        val countryTag = tags.find { it.startsWith("country=") }?.substringAfter("country=")
        if (!countryTag.isNullOrBlank()) return countryTag

        val location = spot.locationName.lowercase()
        return when {
            location.contains("lisbon") || location.contains("alfama") || location.contains("chiado") || location.contains("sintra") || location.contains("portugal") -> "Portugal"
            location.contains("dresden") || location.contains("germany") -> "Germany"
            location.contains("paris") || location.contains("france") -> "France"
            location.contains("madrid") || location.contains("spain") -> "Spain"
            else -> "Portugal"
        }
    }
}

/**
 * Utility evaluating discovered spots in Scout Lisbon and updating achievement badge progress in Room DB.
 */
class AchievementEvaluator(
    private val scoutDao: ScoutDao,
    private val achievementDao: AchievementDao
) {

    suspend fun evaluateAllAchievements() = withContext(Dispatchers.IO) {
        val discoveredSpots = scoutDao.getDiscoveredSpotsSync()

        val heritageProg = VarietyAchievementCalculator.calculateHeritageVariety(discoveredSpots)
        val districtProg = VarietyAchievementCalculator.calculateDistrictVariety(discoveredSpots)
        val countryProg = VarietyAchievementCalculator.calculateCountryVariety(discoveredSpots)

        updateAchievement(heritageProg.id, heritageProg.currentCount, heritageProg.goalCount)
        updateAchievement(districtProg.id, districtProg.currentCount, districtProg.goalCount)
        updateAchievement(countryProg.id, countryProg.currentCount, countryProg.goalCount)
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
