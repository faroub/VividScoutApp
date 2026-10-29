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

    @Query("SELECT * FROM scout_spots WHERE isDiscovered = 1 ORDER BY discoveredTimestamp DESC")
    fun getDiscoveredSpots(): Flow<List<ScoutSpotEntity>>

    @Query("SELECT * FROM scout_spots WHERE isDiscovered = 1")
    suspend fun getDiscoveredSpotsSync(): List<ScoutSpotEntity>

    @Query("SELECT * FROM scout_spots")
    suspend fun getAllSpotsSync(): List<ScoutSpotEntity>

    @Query("SELECT * FROM scout_spots WHERE isOfflineDraft = 1 ORDER BY timestamp DESC")
    fun getOfflineDrafts(): Flow<List<ScoutSpotEntity>>

    @Query("SELECT * FROM scout_spots WHERE id = :id LIMIT 1")
    suspend fun getSpotById(id: String): ScoutSpotEntity?

    @Query("SELECT * FROM scout_spots WHERE category = :category ORDER BY timestamp DESC")
    fun getSpotsByCategory(category: String): Flow<List<ScoutSpotEntity>>

    @Query("SELECT * FROM scout_spots WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchSpots(query: String): Flow<List<ScoutSpotEntity>>

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

    @Query("UPDATE scout_spots SET isDiscovered = :isDiscovered, discoveredTimestamp = :timestamp WHERE id = :id")
    suspend fun setDiscoveredStatus(id: String, isDiscovered: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE scout_spots SET userNotes = :notes WHERE id = :id")
    suspend fun updateUserNotes(id: String, notes: String)

    @Query("UPDATE scout_spots SET upvotes = upvotes + 1 WHERE id = :id")
    suspend fun incrementUpvote(id: String)

    @Query("SELECT COUNT(*) FROM scout_spots")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM scout_spots WHERE isDiscovered = 1")
    suspend fun getDiscoveredCount(): Int

    @Query("SELECT * FROM scout_spots")
    suspend fun getAllSpotsList(): List<ScoutSpotEntity>

    @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0 AND (latitude < :minLat OR latitude > :maxLat OR longitude < :minLng OR longitude > :maxLng)")
    suspend fun deleteUnsavedDistantSpots(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double)

    @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0 AND id LIKE 'osm_fallback_%'")
    suspend fun clearFallbackSpots()

    @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0 AND timestamp < :cutoffTimestamp")
    suspend fun deleteExpiredSpots(cutoffTimestamp: Long): Int

    @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0 AND id IN (:spotIds)")
    suspend fun deleteSpotsByIds(spotIds: List<String>): Int

    @Query("SELECT * FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0")
    suspend fun getUnsavedTransientSpots(): List<ScoutSpotEntity>

    @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0")
    suspend fun clearAllTransientSpots(): Int
}
