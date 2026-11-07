package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPointsDao {

    /**
     * Records a point award. Conflicts on [UserPointsEntity.awardId] are ignored, which
     * makes awarding idempotent: a retried or replayed activity never double-awards.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun awardPoints(award: UserPointsEntity)

    @Query("SELECT COALESCE(SUM(points), 0) FROM user_points")
    fun observeTotalPoints(): Flow<Int>

    @Query("SELECT COALESCE(SUM(points), 0) FROM user_points")
    suspend fun getTotalPoints(): Int
}
