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
     * Migration from version 7 to version 8:
     * Adds tip_votes table with composite primary key (tipId, userId) and indices on tipId and userId.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tip_votes` (
                    `tipId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`tipId`, `userId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tip_votes_tipId` ON `tip_votes` (`tipId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tip_votes_userId` ON `tip_votes` (`userId`)")
        }
    }

    /**
     * Migration from version 8 to version 9:
     * Adds isSyncedToCloud column (INTEGER NOT NULL DEFAULT 0) to community_tips table.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `community_tips` ADD COLUMN `isSyncedToCloud` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Migration from version 9 to version 10:
     * Adds legacyUpvotes column (INTEGER NOT NULL DEFAULT 0) to community_tips table for Option A upvote separation.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `community_tips` ADD COLUMN `legacyUpvotes` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Migration from version 10 to version 11:
     * Adds isMigrated column (INTEGER NOT NULL DEFAULT 1) to community_tips table.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `community_tips` ADD COLUMN `isMigrated` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * Migration from version 11 to version 12:
     * Resets isMigrated = 0 for existing cached rows to force cloud sync-down verification.
     * Note: The isMigrated column default remains 1 for newly inserted rows, as locally created tips
     * are created in Firestore with legacyUpvotes set and are therefore migrated by design.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE community_tips SET isMigrated = 0")
        }
    }

    /**
     * Migration from version 12 to version 13:
     * Creates cached_places table for geohash-based spatial tile caching.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_places` (
                    `placeId` TEXT NOT NULL,
                    `primaryName` TEXT NOT NULL,
                    `primaryDescription` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `category` TEXT NOT NULL,
                    `imageUrl` TEXT,
                    `sourceUrl` TEXT,
                    `license` TEXT,
                    `languageCode` TEXT NOT NULL,
                    `sourcesJson` TEXT NOT NULL,
                    `tileKey` TEXT NOT NULL,
                    `fetchedTimestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`placeId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_places_tileKey` ON `cached_places` (`tileKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_places_fetchedTimestamp` ON `cached_places` (`fetchedTimestamp`)")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13
    )
}
