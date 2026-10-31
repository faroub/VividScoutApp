package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations for AppDatabase.
 *
 * Version history:
 * - Version 1: Initial schema with scout_spots
 * - Version 2: Added discovered_gems
 * - Version 3: Added recent_searches
 * - Version 4: Added weather_cache
 * - Version 5: Added achievements
 * - Version 6: Added community_tips with indices on spotId, timestamp, isStatusUpdate
 * - Version 7: Placeholder migration establishing the pattern for future version bumps
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `discovered_gems` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `locationName` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `photoUri` TEXT,
                    `notes` TEXT NOT NULL,
                    `discoveredAt` INTEGER NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recent_searches` (
                    `id` TEXT NOT NULL,
                    `queryText` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `weather_cache` (
                    `gemId` TEXT NOT NULL,
                    `temperatureC` REAL NOT NULL,
                    `feelsLikeC` REAL NOT NULL,
                    `precipitationChance` INTEGER NOT NULL,
                    `condition` TEXT NOT NULL,
                    `suitability` TEXT NOT NULL,
                    `suitabilityReason` TEXT NOT NULL,
                    `cachedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`gemId`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `achievements` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `tierLevel` INTEGER NOT NULL,
                    `currentProgress` INTEGER NOT NULL,
                    `targetGoal` INTEGER NOT NULL,
                    `isUnlocked` INTEGER NOT NULL,
                    `unlockedTimestamp` INTEGER NOT NULL,
                    `iconResName` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `community_tips` (
                    `id` TEXT NOT NULL,
                    `spotId` TEXT NOT NULL,
                    `spotTitle` TEXT NOT NULL,
                    `authorName` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `isStatusUpdate` INTEGER NOT NULL,
                    `statusCategory` TEXT NOT NULL,
                    `upvotes` INTEGER NOT NULL,
                    `peerNodeId` TEXT NOT NULL,
                    `isLocallyAuthored` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_community_tips_spotId` ON `community_tips` (`spotId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_community_tips_timestamp` ON `community_tips` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_community_tips_isStatusUpdate` ON `community_tips` (`isStatusUpdate`)")
        }
    }

    /**
     * Migration from version 6 to version 7:
     * Migrates scout_spots table to support nullable statistical metrics
     * (rating REAL, reviewCount INTEGER, upvotes INTEGER) without losing user spots.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scout_spots_new` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `distance` TEXT NOT NULL,
                    `walkMinutes` INTEGER NOT NULL,
                    `rating` REAL,
                    `reviewCount` INTEGER,
                    `imageUrl` TEXT NOT NULL,
                    `badgeLabel` TEXT NOT NULL,
                    `statusBadge` TEXT NOT NULL,
                    `tagsRaw` TEXT NOT NULL,
                    `proTip` TEXT,
                    `contributor` TEXT,
                    `contributorAvatar` TEXT,
                    `upvotes` INTEGER,
                    `isSaved` INTEGER NOT NULL,
                    `isOfflineDraft` INTEGER NOT NULL,
                    `isDiscovered` INTEGER NOT NULL,
                    `discoveredTimestamp` INTEGER,
                    `userNotes` TEXT,
                    `offlineCached` INTEGER NOT NULL,
                    `locationName` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `audioDuration` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `scout_spots_new` (
                    `id`, `title`, `description`, `category`, `distance`, `walkMinutes`,
                    `rating`, `reviewCount`, `imageUrl`, `badgeLabel`, `statusBadge`,
                    `tagsRaw`, `proTip`, `contributor`, `contributorAvatar`, `upvotes`,
                    `isSaved`, `isOfflineDraft`, `isDiscovered`, `discoveredTimestamp`,
                    `userNotes`, `offlineCached`, `locationName`, `latitude`, `longitude`,
                    `audioDuration`, `timestamp`
                )
                SELECT
                    `id`, `title`, `description`, `category`, `distance`, `walkMinutes`,
                    `rating`, CAST(`reviewCount` AS INTEGER), `imageUrl`, `badgeLabel`, `statusBadge`,
                    `tagsRaw`, `proTip`, `contributor`, `contributorAvatar`, `upvotes`,
                    `isSaved`, `isOfflineDraft`, `isDiscovered`, `discoveredTimestamp`,
                    `userNotes`, `offlineCached`, `locationName`, `latitude`, `longitude`,
                    `audioDuration`, `timestamp`
                FROM `scout_spots`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `scout_spots`")
            db.execSQL("ALTER TABLE `scout_spots_new` RENAME TO `scout_spots`")
        }
    }

    /**
     * Placeholder migration from version 7 to version 8.
     * Establishes the pattern for the next schema bump without dropping user tables.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA user_version = 8")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )
}
