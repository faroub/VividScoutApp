package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey
    val gemId: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val precipitationChance: Int,
    val condition: String,
    val suitability: String,
    val suitabilityReason: String,
    val cachedAtMillis: Long = System.currentTimeMillis()
)
