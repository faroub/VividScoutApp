package com.example.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseTest {

    private val TEST_DB = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun testMigration1To2PreservesSpotsAndCreatesDiscoveredGems() {
        // Create DB at version 1 and seed a saved spot
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO scout_spots (
                    id, title, description, category, distance, walkMinutes, rating, reviewCount,
                    imageUrl, badgeLabel, statusBadge, tagsRaw, upvotes, isSaved, isOfflineDraft,
                    isDiscovered, offlineCached, locationName, latitude, longitude, timestamp
                ) VALUES (
                    'spot_v1', 'Miradouro v1', 'Historic Overlook', 'sights', '300m', 4, 4.9, '100',
                    'https://example.com/v1.jpg', 'SIGHTS', 'Active', 'scenic', 10, 1, 0,
                    0, 1, 'Lisbon', 38.7118, -9.1306, 1700000000000
                )
                """.trimIndent()
            )
            close()
        }

        // Migrate to version 2 using MIGRATION_1_2
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, DatabaseMigrations.MIGRATION_1_2)

        // Verify spot survives
        val cursor: Cursor = db.query("SELECT * FROM scout_spots WHERE id = 'spot_v1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Miradouro v1", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isSaved")))
        cursor.close()

        // Verify discovered_gems table exists and works
        db.execSQL(
            """
            INSERT INTO discovered_gems (
                id, title, description, category, locationName, latitude, longitude, notes, discoveredAt, isSynced
            ) VALUES (
                'gem_v2', 'Discovered v2', 'Found spot', 'sights', 'Lisbon', 38.7112, -9.1423, 'Great note', 1700000001000, 0
            )
            """.trimIndent()
        )
        val gemCursor = db.query("SELECT * FROM discovered_gems WHERE id = 'gem_v2'")
        assertTrue(gemCursor.moveToFirst())
        assertEquals("Discovered v2", gemCursor.getString(gemCursor.getColumnIndexOrThrow("title")))
        gemCursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigration2To3CreatesRecentSearches() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, DatabaseMigrations.MIGRATION_2_3)

        db.execSQL(
            "INSERT INTO recent_searches (id, queryText, category, timestamp) VALUES ('search_1', 'Azulejo Tiles', 'sights', 1700000000000)"
        )
        val cursor = db.query("SELECT * FROM recent_searches WHERE id = 'search_1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Azulejo Tiles", cursor.getString(cursor.getColumnIndexOrThrow("queryText")))
        cursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigration3To4CreatesWeatherCache() {
        var db = helper.createDatabase(TEST_DB, 3).apply {
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, DatabaseMigrations.MIGRATION_3_4)

        db.execSQL(
            """
            INSERT INTO weather_cache (
                gemId, temperatureC, feelsLikeC, precipitationChance, condition, suitability, suitabilityReason, cachedAtMillis
            ) VALUES (
                'gem_weather', 22.5, 23.0, 10, 'Sunny', 'GREAT', 'Mild afternoon', 1700000000000
            )
            """.trimIndent()
        )
        val cursor = db.query("SELECT * FROM weather_cache WHERE gemId = 'gem_weather'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Sunny", cursor.getString(cursor.getColumnIndexOrThrow("condition")))
        cursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigration4To5CreatesAchievements() {
        var db = helper.createDatabase(TEST_DB, 4).apply {
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5)

        db.execSQL(
            """
            INSERT INTO achievements (
                id, title, description, category, tierLevel, currentProgress, targetGoal, isUnlocked, unlockedTimestamp, iconResName
            ) VALUES (
                'badge_1', 'Lisbon Explorer', 'Explore Lisbon', 'sights', 1, 2, 3, 0, 0, 'ic_cat_sights'
            )
            """.trimIndent()
        )
        val cursor = db.query("SELECT * FROM achievements WHERE id = 'badge_1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Lisbon Explorer", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        cursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigration5To6CreatesCommunityTipsAndIndices() {
        var db = helper.createDatabase(TEST_DB, 5).apply {
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6)

        db.execSQL(
            """
            INSERT INTO community_tips (
                id, spotId, spotTitle, authorName, content, timestamp, isStatusUpdate, statusCategory, upvotes, peerNodeId, isLocallyAuthored
            ) VALUES (
                'tip_1', 'spot_1', 'Miradouro', '@maria', 'Arrive before 9 AM', 1700000000000, 0, 'INSIGHT', 5, 'peer-1', 1
            )
            """.trimIndent()
        )
        val cursor = db.query("SELECT * FROM community_tips WHERE id = 'tip_1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("@maria", cursor.getString(cursor.getColumnIndexOrThrow("authorName")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isLocallyAuthored")))
        cursor.close()

        // Verify indices exist
        val indexCursor = db.query("PRAGMA index_list('community_tips')")
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()

        assertTrue(indexNames.contains("index_community_tips_spotId"))
        assertTrue(indexNames.contains("index_community_tips_timestamp"))
        assertTrue(indexNames.contains("index_community_tips_isStatusUpdate"))
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigration6To7MigratesNullableStatisticsAndPreservesSpots() {
        var db = helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO scout_spots (
                    id, title, description, category, distance, walkMinutes, rating, reviewCount,
                    imageUrl, badgeLabel, statusBadge, tagsRaw, upvotes, isSaved, isOfflineDraft,
                    isDiscovered, offlineCached, locationName, latitude, longitude, timestamp
                ) VALUES (
                    'spot_v6', 'Santa Luzia v6', 'Alfama overlook', 'sights', '350m', 5, 4.9, '1800',
                    'https://example.com/v6.jpg', 'SIGHTS', 'Active', 'sunny', 142, 1, 0,
                    1, 1, 'Lisbon', 38.7118, -9.1306, 1700000000000
                )
                """.trimIndent()
            )
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, DatabaseMigrations.MIGRATION_6_7)

        val cursor = db.query("SELECT * FROM scout_spots WHERE id = 'spot_v6'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Santa Luzia v6", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(4.9, cursor.getDouble(cursor.getColumnIndexOrThrow("rating")), 0.001)
        assertEquals(1800, cursor.getInt(cursor.getColumnIndexOrThrow("reviewCount")))
        assertEquals(142, cursor.getInt(cursor.getColumnIndexOrThrow("upvotes")))
        cursor.close()

        // Insert new spot with null statistical fields at version 7
        db.execSQL(
            """
            INSERT INTO scout_spots (
                id, title, description, category, distance, walkMinutes, rating, reviewCount,
                imageUrl, badgeLabel, statusBadge, tagsRaw, upvotes, isSaved, isOfflineDraft,
                isDiscovered, offlineCached, locationName, latitude, longitude, timestamp
            ) VALUES (
                'spot_v7_nulls', 'Unreviewed Secret Spot', 'Raw undiscovered gem', 'gems', '100m', 1, NULL, NULL,
                'https://example.com/v7.jpg', 'GEMS', 'Active', 'hidden', NULL, 0, 0,
                0, 1, 'Lisbon', 38.7112, -9.1423, 1700000006000
            )
            """.trimIndent()
        )
        val nullCursor = db.query("SELECT * FROM scout_spots WHERE id = 'spot_v7_nulls'")
        assertTrue(nullCursor.moveToFirst())
        assertTrue(nullCursor.isNull(nullCursor.getColumnIndexOrThrow("rating")))
        assertTrue(nullCursor.isNull(nullCursor.getColumnIndexOrThrow("reviewCount")))
        assertTrue(nullCursor.isNull(nullCursor.getColumnIndexOrThrow("upvotes")))
        nullCursor.close()

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigration7To8PlaceholderPattern() {
        val db = helper.createDatabase(TEST_DB, 7)
        assertEquals(7, db.version)
        assertEquals(7, DatabaseMigrations.MIGRATION_7_8.startVersion)
        assertEquals(8, DatabaseMigrations.MIGRATION_7_8.endVersion)
        DatabaseMigrations.MIGRATION_7_8.migrate(db)
        val versionCursor = db.query("PRAGMA user_version")
        assertTrue(versionCursor.moveToFirst())
        assertEquals(8, versionCursor.getInt(0))
        versionCursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testAllMigrationsFromV1ToV7PreserveAllUserData() {
        // Seed user rows at version 1
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO scout_spots (
                    id, title, description, category, distance, walkMinutes, rating, reviewCount,
                    imageUrl, badgeLabel, statusBadge, tagsRaw, upvotes, isSaved, isOfflineDraft,
                    isDiscovered, offlineCached, locationName, latitude, longitude, timestamp
                ) VALUES (
                    'spot_keep', 'Pergola Nook', 'Saved by user', 'sights', '350m', 5, 4.9, '1.8k',
                    'https://example.com/nook.jpg', 'SIGHTS', 'Active', 'peaceful', 42, 1, 0,
                    1, 1, 'Lisbon', 38.7118, -9.1306, 1700000000000
                )
                """.trimIndent()
            )
            close()
        }

        // Migrate incrementally from v1 -> v2 -> v3 -> v4 -> v5 -> v6 -> v7
        // At v2, seed discovered gem
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, DatabaseMigrations.MIGRATION_1_2).apply {
            execSQL(
                """
                INSERT INTO discovered_gems (
                    id, title, description, category, locationName, latitude, longitude, notes, discoveredAt, isSynced
                ) VALUES (
                    'gem_keep', 'Secret Fountain', 'Water fountain in Alfama', 'sights', 'Alfama', 38.7112, -9.1423, 'Must visit', 1700000001000, 1
                )
                """.trimIndent()
            )
            close()
        }

        // At v3, seed recent search
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, DatabaseMigrations.MIGRATION_2_3).apply {
            execSQL(
                "INSERT INTO recent_searches (id, queryText, category, timestamp) VALUES ('search_keep', 'Custard tarts', 'eats', 1700000002000)"
            )
            close()
        }

        // At v4, seed weather cache
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, DatabaseMigrations.MIGRATION_3_4).apply {
            execSQL(
                """
                INSERT INTO weather_cache (
                    gemId, temperatureC, feelsLikeC, precipitationChance, condition, suitability, suitabilityReason, cachedAtMillis
                ) VALUES (
                    'gem_keep', 24.0, 24.5, 0, 'Clear', 'EXCELLENT', 'Ideal light', 1700000003000
                )
                """.trimIndent()
            )
            close()
        }

        // At v5, seed achievement
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).apply {
            execSQL(
                """
                INSERT INTO achievements (
                    id, title, description, category, tierLevel, currentProgress, targetGoal, isUnlocked, unlockedTimestamp, iconResName
                ) VALUES (
                    'achievement_keep', 'Master Scout', 'Unlocked badge', 'sights', 3, 5, 5, 1, 1700000004000, 'ic_cat_sights'
                )
                """.trimIndent()
            )
            close()
        }

        // Migrate to version 6 (adds community_tips)
        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).apply {
            execSQL(
                """
                INSERT INTO community_tips (
                    id, spotId, spotTitle, authorName, content, timestamp, isStatusUpdate, statusCategory, upvotes, peerNodeId, isLocallyAuthored
                ) VALUES (
                    'tip_keep', 'spot_keep', 'Pergola Nook', '@scout_traveler', 'Best sunset view', 1700000005000, 0, 'INSIGHT', 99, 'peer-node', 1
                )
                """.trimIndent()
            )
            close()
        }

        // Migrate to version 7 (migrates scout_spots to nullable statistics)
        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, DatabaseMigrations.MIGRATION_6_7)

        // Verify that ALL seeded user records across all entity types survive at version 7
        // 1. Saved spots
        val spotCursor = db.query("SELECT * FROM scout_spots WHERE id = 'spot_keep'")
        assertTrue("Spot row must survive", spotCursor.moveToFirst())
        assertEquals("Pergola Nook", spotCursor.getString(spotCursor.getColumnIndexOrThrow("title")))
        assertEquals(1, spotCursor.getInt(spotCursor.getColumnIndexOrThrow("isSaved")))
        assertEquals(1, spotCursor.getInt(spotCursor.getColumnIndexOrThrow("isDiscovered")))
        assertEquals(4.9, spotCursor.getDouble(spotCursor.getColumnIndexOrThrow("rating")), 0.001)
        assertEquals(42, spotCursor.getInt(spotCursor.getColumnIndexOrThrow("upvotes")))
        spotCursor.close()

        // 2. Discovered gems
        val gemCursor = db.query("SELECT * FROM discovered_gems WHERE id = 'gem_keep'")
        assertTrue("Discovered gem must survive", gemCursor.moveToFirst())
        assertEquals("Secret Fountain", gemCursor.getString(gemCursor.getColumnIndexOrThrow("title")))
        assertEquals("Must visit", gemCursor.getString(gemCursor.getColumnIndexOrThrow("notes")))
        gemCursor.close()

        // 3. Recent searches
        val searchCursor = db.query("SELECT * FROM recent_searches WHERE id = 'search_keep'")
        assertTrue("Recent search must survive", searchCursor.moveToFirst())
        assertEquals("Custard tarts", searchCursor.getString(searchCursor.getColumnIndexOrThrow("queryText")))
        searchCursor.close()

        // 4. Weather cache
        val weatherCursor = db.query("SELECT * FROM weather_cache WHERE gemId = 'gem_keep'")
        assertTrue("Weather cache must survive", weatherCursor.moveToFirst())
        assertEquals("Clear", weatherCursor.getString(weatherCursor.getColumnIndexOrThrow("condition")))
        weatherCursor.close()

        // 5. Achievements
        val achievementCursor = db.query("SELECT * FROM achievements WHERE id = 'achievement_keep'")
        assertTrue("Achievement must survive", achievementCursor.moveToFirst())
        assertEquals("Master Scout", achievementCursor.getString(achievementCursor.getColumnIndexOrThrow("title")))
        assertEquals(1, achievementCursor.getInt(achievementCursor.getColumnIndexOrThrow("isUnlocked")))
        achievementCursor.close()

        // 6. Community tips
        val tipCursor = db.query("SELECT * FROM community_tips WHERE id = 'tip_keep'")
        assertTrue("Community tip must survive", tipCursor.moveToFirst())
        assertEquals("Best sunset view", tipCursor.getString(tipCursor.getColumnIndexOrThrow("content")))
        assertEquals(99, tipCursor.getInt(tipCursor.getColumnIndexOrThrow("upvotes")))
        tipCursor.close()

        db.close()
    }
}
