package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ledger entry awarding Scout Points for a completed in-app activity.
 *
 * Points are persisted locally and derived only from this ledger, so the displayed
 * total always reflects real activity and survives restarts. Each award is keyed by
 * a deterministic [awardId] (award reason + the referenced entity id): a retry, sync,
 * or replay of the same activity collides on the primary key and is ignored, so the
 * same activity can never award twice.
 */
@Entity(tableName = "user_points")
data class UserPointsEntity(
    @PrimaryKey val awardId: String,
    val points: Int,
    val reason: String,
    val referenceId: String,
    val timestamp: Long
)
