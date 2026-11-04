package com.example.data.places

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedPlaceDao {

    @Query("SELECT * FROM cached_places WHERE tileKey IN (:tileKeys)")
    suspend fun getPlacesForTiles(tileKeys: List<String>): List<CachedPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<CachedPlaceEntity>)

    @Query("DELETE FROM cached_places WHERE tileKey = :tileKey")
    suspend fun deleteTile(tileKey: String)

    @Transaction
    suspend fun replaceTilePlaces(tileKey: String, newPlaces: List<CachedPlaceEntity>) {
        deleteTile(tileKey)
        insertPlaces(newPlaces)
    }

    @Query("DELETE FROM cached_places WHERE fetchedTimestamp < :expirationTime")
    suspend fun deleteExpiredPlaces(expirationTime: Long)

    @Query("SELECT COUNT(*) FROM cached_places")
    suspend fun getPlaceCount(): Int
}
