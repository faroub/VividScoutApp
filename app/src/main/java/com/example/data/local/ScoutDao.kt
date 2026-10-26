package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoutDao {
    @Query("SELECT * FROM scout_spots ORDER BY timestamp DESC")
    fun getAllSpots(): Flow<List<ScoutSpotEntity>>

    @Query("SELECT * FROM scout_spots WHERE isSaved = 1 ORDER BY timestamp DESC")
    fun getSavedSpots(): Flow<List<ScoutSpotEntity>>

    @Query("SELECT * FROM scout_spots WHERE isOfflineDraft = 1 ORDER BY timestamp DESC")
    fun getOfflineDrafts(): Flow<List<ScoutSpotEntity>>

    @Query("SELECT * FROM scout_spots WHERE id = :id LIMIT 1")
    suspend fun getSpotById(id: String): ScoutSpotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpots(spots: List<ScoutSpotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpot(spot: ScoutSpotEntity)

    @Update
    suspend fun updateSpot(spot: ScoutSpotEntity)

    @Delete
    suspend fun deleteSpot(spot: ScoutSpotEntity)

    @Query("UPDATE scout_spots SET isSaved = :isSaved WHERE id = :id")
    suspend fun setSavedStatus(id: String, isSaved: Boolean)

    @Query("UPDATE scout_spots SET upvotes = upvotes + 1 WHERE id = :id")
    suspend fun incrementUpvote(id: String)

    @Query("SELECT COUNT(*) FROM scout_spots")
    suspend fun getCount(): Int
}
