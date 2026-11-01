package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class TipVoteDao {

    @Query("SELECT COUNT(*) > 0 FROM tip_votes WHERE tipId = :tipId AND userId = :userId")
    abstract suspend fun hasUserVoted(tipId: String, userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertVote(vote: TipVoteEntity)

    @Query("DELETE FROM tip_votes WHERE tipId = :tipId AND userId = :userId")
    abstract suspend fun deleteVote(tipId: String, userId: String)

    @Query("SELECT COUNT(*) FROM tip_votes WHERE tipId = :tipId")
    abstract suspend fun getVoteCountForTip(tipId: String): Int

    /**
     * Executes both recording the user vote in tip_votes AND incrementing tip upvotes in community_tips
     * inside a single Room @Transaction.
     */
    @Transaction
    open suspend fun recordVoteAndIncrementCount(
        tipId: String,
        userId: String,
        communityTipDao: CommunityTipDao
    ) {
        insertVote(TipVoteEntity(tipId = tipId, userId = userId))
        communityTipDao.incrementUpvote(tipId)
    }
}
