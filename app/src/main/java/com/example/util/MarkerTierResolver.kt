package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.data.local.AchievementEntity

/**
 * Visual tier classification for map pins based on user achievement progress.
 */
enum class MarkerTier {
    STANDARD,
    BRONZE,
    SILVER,
    GOLD
}

data class MarkerTierStyle(
    val tier: MarkerTier,
    val auraColor: Color,
    val badgeLabel: String,
    val showSparkle: Boolean,
    val isPulsing: Boolean
)

object MarkerTierResolver {

    val ColorBronze = Color(0xFFD97706)
    val ColorSilver = Color(0xFF94A3B8)
    val ColorGold = Color(0xFFF59E0B)

    /**
     * Resolves the visual marker tier style for a given spot category based on current achievement records.
     */
    fun resolveTierForCategory(
        category: String,
        achievements: List<AchievementEntity>
    ): MarkerTierStyle {
        val cat = category.lowercase().trim()
        val matchingAchievement = achievements.find {
            it.category.lowercase().contains(cat) || cat.contains(it.category.lowercase())
        }

        if (matchingAchievement == null) {
            return MarkerTierStyle(
                tier = MarkerTier.STANDARD,
                auraColor = Color.Transparent,
                badgeLabel = "SCOUT PIN",
                showSparkle = false,
                isPulsing = false
            )
        }

        if (matchingAchievement.isUnlocked) {
            return MarkerTierStyle(
                tier = MarkerTier.GOLD,
                auraColor = ColorGold,
                badgeLabel = "GOLD MASTER • 100%",
                showSparkle = true,
                isPulsing = true
            )
        }

        val pct = matchingAchievement.progressPercentage
        return when {
            pct >= 0.66f -> MarkerTierStyle(
                tier = MarkerTier.SILVER,
                auraColor = ColorSilver,
                badgeLabel = "SILVER • ${(pct * 100).toInt()}%",
                showSparkle = true,
                isPulsing = true
            )
            pct >= 0.33f -> MarkerTierStyle(
                tier = MarkerTier.BRONZE,
                auraColor = ColorBronze,
                badgeLabel = "BRONZE • ${(pct * 100).toInt()}%",
                showSparkle = false,
                isPulsing = true
            )
            else -> MarkerTierStyle(
                tier = MarkerTier.STANDARD,
                auraColor = Color.Transparent,
                badgeLabel = "SCOUT PIN",
                showSparkle = false,
                isPulsing = false
            )
        }
    }
}
