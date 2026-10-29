package com.example.data.model

enum class SuitabilityLevel {
    IDEAL,
    CAUTION,
    UNFAVORABLE;

    fun toLabel(): String {
        return when (this) {
            IDEAL -> "Great Time to Visit"
            CAUTION -> "Caution: Weather Watch"
            UNFAVORABLE -> "Indoor Preferred"
        }
    }
}

data class WeatherReport(
    val gemId: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val precipitationChance: Int,
    val condition: String,
    val suitability: SuitabilityLevel,
    val suitabilityReason: String,
    val cachedAtMillis: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false
) {
    val cacheAgeText: String
        get() {
            val diffMs = System.currentTimeMillis() - cachedAtMillis
            val mins = (diffMs / (1000 * 60)).toInt()
            val hours = mins / 60
            return when {
                mins < 1 -> "Just updated"
                mins < 60 -> "Cached ${mins}m ago"
                hours < 24 -> "Cached ${hours}h ago"
                else -> "Cached >1d ago"
            }
        }
}
