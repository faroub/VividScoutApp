package com.example.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Persisted record of a user's upvote on a community tip.
 * Uses composite primary key (tipId + userId) to enforce single vote per tip per user locally.
 */
@Entity(
    tableName = "tip_votes",
    primaryKeys = ["tipId", "userId"],
    indices = [
        Index(value = ["tipId"]),
        Index(value = ["userId"])
    ]
)
data class TipVoteEntity(
    val tipId: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)
