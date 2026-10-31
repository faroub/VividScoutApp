package com.example.util

import com.example.data.model.SuitabilityLevel

object WeatherSuitabilityEvaluator {

    fun evaluate(
        category: String,
        tempC: Double,
        feelsLikeC: Double,
        precipChance: Int,
        condition: String
    ): Pair<SuitabilityLevel, String> {
        val catLower = category.lowercase()
        val isOutdoor = catLower.contains("sights") || catLower.contains("shortcut") ||
                catLower.contains("viewpoint") || catLower.contains("beach") ||
                catLower.contains("park") || catLower.contains("overlook")

        return when {
            // High rain risk
            precipChance >= 60 -> {
                if (isOutdoor) {
                    Pair(
                        SuitabilityLevel.UNFAVORABLE,
                        "High rain chance ($precipChance%). Consider indoor sights or museums today."
                    )
                } else {
                    Pair(
                        SuitabilityLevel.IDEAL,
                        "Heavy rain forecast ($precipChance%). Perfect time for this covered/indoor gem!"
                    )
                }
            }

            // Moderate rain risk
            precipChance >= 30 -> {
                if (isOutdoor) {
                    Pair(
                        SuitabilityLevel.CAUTION,
                        "Shower risk ($precipChance%). Pack an umbrella for outdoor views."
                    )
                } else {
                    Pair(
                        SuitabilityLevel.IDEAL,
                        "Light shower risk ($precipChance%). Good shelter for visiting."
                    )
                }
            }

            // Extreme heat (feels like > 32C)
            feelsLikeC >= 32.0 -> {
                if (isOutdoor) {
                    Pair(
                        SuitabilityLevel.CAUTION,
                        "High heat (Feels like ${feelsLikeC.toInt()}°C). Bring water and seek shade."
                    )
                } else {
                    Pair(
                        SuitabilityLevel.IDEAL,
                        "Air-conditioned indoor spot to escape the heat (Feels like ${feelsLikeC.toInt()}°C)."
                    )
                }
            }

            // Cold / Chilly (feels like < 12C)
            feelsLikeC <= 12.0 -> {
                if (isOutdoor) {
                    Pair(
                        SuitabilityLevel.CAUTION,
                        "Brisk weather (Feels like ${feelsLikeC.toInt()}°C). Dress warmly for wind on the hills."
                    )
                } else {
                    Pair(
                        SuitabilityLevel.IDEAL,
                        "Cozy warm atmosphere inside (Feels like ${feelsLikeC.toInt()}°C outside)."
                    )
                }
            }

            // Pleasant mild conditions
            else -> {
                Pair(
                    SuitabilityLevel.IDEAL,
                    "Ideal outdoor conditions! Comfortable ${feelsLikeC.toInt()}°C feels-like with low rain risk ($precipChance%)."
                )
            }
        }
    }
}
