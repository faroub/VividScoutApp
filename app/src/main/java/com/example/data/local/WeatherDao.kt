package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE gemId = :gemId")
    fun getWeatherForGem(gemId: String): Flow<WeatherCacheEntity?>

    @Query("SELECT * FROM weather_cache WHERE gemId = :gemId")
    suspend fun getWeatherSync(gemId: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache WHERE gemId = :gemId")
    suspend fun deleteWeatherForGem(gemId: String)

    @Query("DELETE FROM weather_cache")
    suspend fun clearAllWeather(): Int

    @Query("SELECT COUNT(*) FROM weather_cache")
    suspend fun getWeatherCount(): Int
}
