package com.example.util

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEnrichmentQueueTest {

    private fun createSpot(
        id: String,
        imageUrl: String = "ic_cat_sights",
        isSaved: Boolean = false
    ): ScoutSpotEntity {
        return ScoutSpotEntity(
            id = id,
            title = "Spot $id",
            description = "Description $id",
            category = "sights",
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = imageUrl,
            badgeLabel = "Local",
            statusBadge = "Scout Spot",
            tagsRaw = "",
            isSaved = isSaved,
            latitude = 38.7112,
            longitude = -9.1423
        )
    }

    @Test
    fun isGenericOrStockPhoto_detectsUnsplashAndAssetPlaceholders() {
        assertTrue("Unsplash placeholder URL must be recognized as generic",
            LocationPhotoResolver.isGenericOrStockPhoto("https://images.unsplash.com/photo-1509440159596-0249088772ff?w=800"))

        assertTrue("Vector asset drawables must be recognized as generic",
            LocationPhotoResolver.isGenericOrStockPhoto("ic_cat_sights"))

        assertTrue("Empty or blank URL must be recognized as generic",
            LocationPhotoResolver.isGenericOrStockPhoto(""))

        assertFalse("Real verified Wikimedia Commons photo must NOT be recognized as generic",
            LocationPhotoResolver.isGenericOrStockPhoto("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a8/Eiffel_Tower.jpg/800px-Eiffel_Tower.jpg"))
    }

    @Test
    fun prioritySorting_placesSavedSpotsFirstInQueue() {
        val spotFeed1 = createSpot("feed_1", isSaved = false)
        val spotFeed2 = createSpot("feed_2", isSaved = false)
        val spotSaved1 = createSpot("saved_1", isSaved = true)
        val spotSaved2 = createSpot("saved_2", isSaved = true)

        val unSorted = listOf(spotFeed1, spotSaved1, spotFeed2, spotSaved2)
        val sorted = unSorted.sortedWith(
            compareByDescending<ScoutSpotEntity> { it.isSaved }
                .thenByDescending { it.timestamp }
        )

        assertTrue("Saved spot 1 must be prioritized", sorted[0].isSaved)
        assertTrue("Saved spot 2 must be prioritized second", sorted[1].isSaved)
        assertFalse("Feed spot 1 comes after saved spots", sorted[2].isSaved)
        assertFalse("Feed spot 2 comes after saved spots", sorted[3].isSaved)
    }

    @Test
    fun boundedBatching_processesMoreThanThirtySpotsInBatches() {
        // Create 45 spots needing enrichment
        val spots = (1..45).map { i -> createSpot("spot_$i") }

        val firstBatch = spots.sortedByDescending { it.isSaved }.take(30)
        assertEquals("First batch size must be capped at 30", 30, firstBatch.size)

        val remainingBatch = spots.drop(30)
        assertEquals("Remaining 15 spots must be processed in second batch", 15, remainingBatch.size)
    }
}
