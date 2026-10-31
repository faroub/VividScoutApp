package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a saved Gemini-driven discovery query for quick re-execution.
 * Persisted in local Room database with maximum capacity of 5 LRU queries.
 */
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val queryText: String,
    val category: String = "all",
    val timestamp: Long = System.currentTimeMillis()
)
