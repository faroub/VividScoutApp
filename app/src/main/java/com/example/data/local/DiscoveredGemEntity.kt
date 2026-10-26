package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovered_gems")
data class DiscoveredGemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val locationName: String = "Lisbon",
    val latitude: Double = 38.7112,
    val longitude: Double = -9.1423,
    val photoUri: String? = null,
    val notes: String = "",
    val discoveredAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
