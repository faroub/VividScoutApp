package com.example.data.model

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheHealthTest {

    private fun createSpot(id: String, lat: Double, lng: Double): ScoutSpotEntity {
        return ScoutSpotEntity(
            id = id,
            title = "Test Spot $id",
            description = "Description",
            category = "sights",
            distance = "100m",
            walkMinutes = 2,
            rating = 4.8,
            reviewCount = "10",
            imageUrl = "",
            badgeLabel = "GEM",
            statusBadge = "VERIFIED",
            tagsRaw = "sight",
            latitude = lat,
            longitude = lng
        )
    }

    @Test
    fun testCacheHealth_emptyCacheReturnsCritical() {
        val userLat = 38.7112
        val userLng = -9.1423
        val health = CacheHealthCalculator.calculate(
            userLat = userLat,
            userLng = userLng,
            spots = emptyList(),
            radiusMeters = 1000,
            targetCount = 12
        )

        assertEquals(0, health.cachedCount)
        assertEquals(0, health.scorePercent)
        assertEquals(12, health.neededToFetch)
        assertEquals(CacheHealthLevel.CRITICAL, health.level)
    }

    @Test
    fun testCacheHealth_partialCacheReturnsFairAndGood() {
        val userLat = 38.7112
        val userLng = -9.1423

        // Place 4 spots within 400m
        val spotsFair = (1..4).map {
            createSpot("spot_$it", userLat + 0.001 * it, userLng)
        }
        val healthFair = CacheHealthCalculator.calculate(
            userLat = userLat,
            userLng = userLng,
            spots = spotsFair,
            radiusMeters = 1000,
            targetCount = 12
        )

        assertEquals(4, healthFair.cachedCount)
        // 4/12 = 33%
        assertEquals(33, healthFair.scorePercent)
        assertEquals(8, healthFair.neededToFetch)
        assertEquals(CacheHealthLevel.FAIR, healthFair.level)

        // Place 8 spots within 400m
        val spotsGood = (1..8).map {
            createSpot("spot_$it", userLat + 0.0005 * it, userLng)
        }
        val healthGood = CacheHealthCalculator.calculate(
            userLat = userLat,
            userLng = userLng,
            spots = spotsGood,
            radiusMeters = 1000,
            targetCount = 12
        )

        assertEquals(8, healthGood.cachedCount)
        // 8/12 = 66%
        assertEquals(66, healthGood.scorePercent)
        assertEquals(4, healthGood.neededToFetch)
        assertEquals(CacheHealthLevel.GOOD, healthGood.level)
    }

    @Test
    fun testCacheHealth_optimalCacheExceedsTarget() {
        val userLat = 38.7112
        val userLng = -9.1423

        val spots = (1..15).map {
            createSpot("spot_$it", userLat + 0.0002 * it, userLng)
        }
        val health = CacheHealthCalculator.calculate(
            userLat = userLat,
            userLng = userLng,
            spots = spots,
            radiusMeters = 1000,
            targetCount = 12
        )

        assertEquals(15, health.cachedCount)
        assertEquals(100, health.scorePercent)
        assertEquals(0, health.neededToFetch)
        assertEquals(CacheHealthLevel.OPTIMAL, health.level)
        assertTrue(health.isOptimal)
    }

    @Test
    fun testCacheHealth_ignoresSpotsBeyondRadius() {
        val userLat = 38.7112
        val userLng = -9.1423

        // 2 spots within 500m
        val closeSpots = listOf(
            createSpot("close_1", userLat + 0.001, userLng),
            createSpot("close_2", userLat - 0.001, userLng)
        )
        // 10 spots 20km away
        val farSpots = (1..10).map {
            createSpot("far_$it", userLat + 0.2, userLng + 0.2)
        }

        val health = CacheHealthCalculator.calculate(
            userLat = userLat,
            userLng = userLng,
            spots = closeSpots + farSpots,
            radiusMeters = 1000,
            targetCount = 10
        )

        assertEquals(2, health.cachedCount)
        assertEquals(20, health.scorePercent)
        assertEquals(8, health.neededToFetch)
        assertEquals(CacheHealthLevel.CRITICAL, health.level)
    }
}
