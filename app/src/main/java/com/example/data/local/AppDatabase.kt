package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ScoutSpotEntity::class,
        DiscoveredGemEntity::class,
        RecentSearchEntity::class,
        WeatherCacheEntity::class,
        AchievementEntity::class,
        CommunityTipEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoutDao(): ScoutDao
    abstract fun discoveredGemDao(): DiscoveredGemDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun weatherDao(): WeatherDao
    abstract fun achievementDao(): AchievementDao
    abstract fun communityTipDao(): CommunityTipDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vivid_scout_database"
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialSpots(database.scoutDao())
                        populateInitialRecentSearches(database.recentSearchDao())
                        populateInitialAchievements(database.achievementDao())
                        populateInitialCommunityTips(database.communityTipDao())
                    }
                }
            }
        }

        suspend fun populateInitialRecentSearches(dao: RecentSearchDao) {
            val now = System.currentTimeMillis()
            val initialSearches = listOf(
                RecentSearchEntity(
                    id = "search_seed_1",
                    queryText = "Secret Azulejo Viewpoints",
                    category = "sights",
                    timestamp = now
                ),
                RecentSearchEntity(
                    id = "search_seed_2",
                    queryText = "Artisanal Pastel Pastries",
                    category = "eats",
                    timestamp = now - 60_000L
                ),
                RecentSearchEntity(
                    id = "search_seed_3",
                    queryText = "Historic Fado Taverns",
                    category = "history",
                    timestamp = now - 120_000L
                )
            )
            for (search in initialSearches) {
                dao.insertSearch(search)
            }
        }

        suspend fun populateInitialSpots(dao: ScoutDao) {
            // On fresh install, do not prepopulate default Lisbon seed spots.
            // Spots are dynamically discovered for the user's active GPS coordinates / selected city.
        }

        suspend fun populateInitialAchievements(dao: AchievementDao) {
            val initialAchievements = listOf(
                AchievementEntity(
                    id = "sights_explorer",
                    title = "Sights Explorer",
                    description = "Discover 3 scenic viewpoints, miradouros, or panorama terraces in Alfama.",
                    category = "sights",
                    tierLevel = 1,
                    currentProgress = 1,
                    targetGoal = 3,
                    isUnlocked = false,
                    iconResName = "ic_cat_sights"
                ),
                AchievementEntity(
                    id = "foodie_scout",
                    title = "Foodie Scout",
                    description = "Sample 3 local bakery tarts, standing cafes, or artisan dining spots.",
                    category = "eats",
                    tierLevel = 1,
                    currentProgress = 0,
                    targetGoal = 3,
                    isUnlocked = false,
                    iconResName = "ic_cat_eats"
                ),
                AchievementEntity(
                    id = "history_buff",
                    title = "History Buff",
                    description = "Explore 2 historic convents, castle ramparts, or gothic ruins.",
                    category = "history",
                    tierLevel = 2,
                    currentProgress = 0,
                    targetGoal = 2,
                    isUnlocked = false,
                    iconResName = "ic_cat_history"
                ),
                AchievementEntity(
                    id = "shortcut_master",
                    title = "Shortcut Master",
                    description = "Uncover 2 secret tile tunnels or cobblestone pedestrian staircases.",
                    category = "shortcut",
                    tierLevel = 2,
                    currentProgress = 1,
                    targetGoal = 2,
                    isUnlocked = false,
                    iconResName = "ic_cat_shortcut"
                ),
                AchievementEntity(
                    id = "offline_pioneer",
                    title = "Offline Pioneer",
                    description = "Log 1 custom offline scout draft in Room database without cellular network.",
                    category = "offline",
                    tierLevel = 3,
                    currentProgress = 1,
                    targetGoal = 1,
                    isUnlocked = true,
                    unlockedTimestamp = System.currentTimeMillis() - 86400000L,
                    iconResName = "ic_cat_secret"
                )
            )
            dao.insertAchievements(initialAchievements)
        }

        suspend fun populateInitialCommunityTips(dao: CommunityTipDao) {
            val initialTips = com.example.data.p2p.PeerMeshSyncEngine.createSeedTips()
            dao.insertTips(initialTips)
        }
    }
}
