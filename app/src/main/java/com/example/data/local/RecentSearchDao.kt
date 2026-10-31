package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 5")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 5")
    suspend fun getRecentSearchesList(): List<RecentSearchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: RecentSearchEntity)

    @Query("SELECT * FROM recent_searches WHERE LOWER(queryText) = LOWER(:queryText) LIMIT 1")
    suspend fun findByQueryText(queryText: String): RecentSearchEntity?

    @Query("DELETE FROM recent_searches WHERE id = :id")
    suspend fun deleteSearchById(id: String): Int

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllSearches(): Int

    @Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY timestamp DESC LIMIT 5)")
    suspend fun trimExcessSearches(): Int

    @Query("SELECT COUNT(*) FROM recent_searches")
    suspend fun getCount(): Int
}
