package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveredGemDao {
    @Query("SELECT * FROM discovered_gems ORDER BY discoveredAt DESC")
    fun getAllDiscoveredGems(): Flow<List<DiscoveredGemEntity>>

    @Query("SELECT * FROM discovered_gems WHERE isSynced = 0 ORDER BY discoveredAt DESC")
    fun getUnsyncedGems(): Flow<List<DiscoveredGemEntity>>

    @Query("SELECT * FROM discovered_gems WHERE id = :id LIMIT 1")
    suspend fun getGemById(id: String): DiscoveredGemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscoveredGem(gem: DiscoveredGemEntity)

    @Update
    suspend fun updateDiscoveredGem(gem: DiscoveredGemEntity)

    @Delete
    suspend fun deleteDiscoveredGem(gem: DiscoveredGemEntity)

    @Query("DELETE FROM discovered_gems WHERE id = :id")
    suspend fun deleteGemById(id: String)

    @Query("SELECT COUNT(*) FROM discovered_gems")
    suspend fun getCount(): Int

    @Query("UPDATE discovered_gems SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}
