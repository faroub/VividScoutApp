package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an earned digital badge achievement in Scout Lisbon.
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val tierLevel: Int = 1, // 1 = Bronze, 2 = Silver, 3 = Gold
    val currentProgress: Int = 0,
    val targetGoal: Int = 3,
    val isUnlocked: Boolean = false,
    val unlockedTimestamp: Long = 0L,
    val iconResName: String = "ic_cat_sights"
) {
    val progressPercentage: Float
        get() = (currentProgress.toFloat() / targetGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
}
