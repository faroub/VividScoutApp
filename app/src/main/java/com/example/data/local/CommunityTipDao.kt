package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityTipDao {

    @Query("SELECT * FROM community_tips ORDER BY timestamp DESC")
    fun getAllTipsFlow(): Flow<List<CommunityTipEntity>>

    @Query("SELECT * FROM community_tips WHERE spotId = :spotId ORDER BY timestamp DESC")
    fun getTipsForSpotFlow(spotId: String): Flow<List<CommunityTipEntity>>

    @Query("SELECT * FROM community_tips WHERE spotId = :spotId AND isStatusUpdate = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getLatestStatusForSpot(spotId: String): Flow<CommunityTipEntity?>

    @Query("SELECT * FROM community_tips WHERE isStatusUpdate = 1 ORDER BY timestamp DESC")
    fun getAllStatusUpdatesFlow(): Flow<List<CommunityTipEntity>>

    @Query("SELECT * FROM community_tips WHERE id = :tipId LIMIT 1")
    suspend fun getTipById(tipId: String): CommunityTipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTip(tip: CommunityTipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTips(tips: List<CommunityTipEntity>)

    @Update
    suspend fun updateTip(tip: CommunityTipEntity)

    @Query("UPDATE community_tips SET upvotes = upvotes + 1 WHERE id = :tipId")
    suspend fun incrementUpvote(tipId: String)

    @Query("DELETE FROM community_tips WHERE id = :tipId")
    suspend fun deleteTip(tipId: String)

    @Query("SELECT COUNT(*) FROM community_tips")
    suspend fun getTipCount(): Int
}
