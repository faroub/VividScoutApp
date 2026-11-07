package com.example.data.places

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaceCoverageDao {

    /**
     * The latest coverage record per provider (one row per provider id — the newest
     * successful query replaces the previous scope).
     */
    @Query("SELECT * FROM place_coverage")
    suspend fun getAll(): List<PlaceCoverageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(coverages: List<PlaceCoverageEntity>)

    @Query("DELETE FROM place_coverage WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: String)

    @Query("DELETE FROM place_coverage WHERE fetchedAt < :expirationTime")
    suspend fun deleteExpired(expirationTime: Long)

    @Query("SELECT COUNT(*) FROM place_coverage")
    suspend fun getCount(): Int
}
