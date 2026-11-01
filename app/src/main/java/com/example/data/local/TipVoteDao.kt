package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TipVoteDao {

    @Query("SELECT COUNT(*) > 0 FROM tip_votes WHERE tipId = :tipId AND userId = :userId")
    suspend fun hasUserVoted(tipId: String, userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: TipVoteEntity)

    @Query("DELETE FROM tip_votes WHERE tipId = :tipId AND userId = :userId")
    suspend fun deleteVote(tipId: String, userId: String)

    @Query("SELECT COUNT(*) FROM tip_votes WHERE tipId = :tipId")
    suspend fun getVoteCountForTip(tipId: String): Int
}
