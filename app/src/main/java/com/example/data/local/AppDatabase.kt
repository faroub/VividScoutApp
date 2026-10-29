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
    entities = [ScoutSpotEntity::class, DiscoveredGemEntity::class, RecentSearchEntity::class, WeatherCacheEntity::class, AchievementEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoutDao(): ScoutDao
    abstract fun discoveredGemDao(): DiscoveredGemDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun weatherDao(): WeatherDao
    abstract fun achievementDao(): AchievementDao

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
                    .fallbackToDestructiveMigration(dropAllTables = true)
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
            val initialSpots = listOf(
                ScoutSpotEntity(
                    id = "spot_1",
                    title = "Miradouro de Santa Luzia — Secret Pergola Nook",
                    description = "Tucked right behind the crowded main balustrade is a shaded stone archway with blue azulejo tiles and unobstructed panorama over the Alfama rooftops.",
                    category = "sights",
                    distance = "350m walk",
                    walkMinutes = 5,
                    rating = 4.9,
                    reviewCount = "1.8k",
                    imageUrl = "ic_cat_sights",
                    badgeLabel = "SIGHTS",
                    statusBadge = "Scraped 8m ago",
                    tagsRaw = "Free Entry|||Quiet sunny nook right now|||Bougainvillea Bloom",
                    proTip = "Morning 8-10 AM has direct warm golden light and zero tour groups.",
                    contributor = "@marco_lisbon",
                    upvotes = 142,
                    isSaved = true,
                    isDiscovered = true,
                    discoveredTimestamp = System.currentTimeMillis() - 86400000L * 2,
                    userNotes = "Breathtaking view through the tiled archway at sunset. Peaceful spot to journal.",
                    locationName = "Alfama Balustrade",
                    latitude = 38.7118,
                    longitude = -9.1306
                ),
                ScoutSpotEntity(
                    id = "spot_2",
                    title = "Pastelaria Santo António — Warm Custard Batch",
                    description = "Real-time sensor hit from the local bakery queue feed: fresh cinnamon-dusted custard tarts pulled directly from baking racks 4 minutes ago.",
                    category = "eats",
                    distance = "180m walk",
                    walkMinutes = 3,
                    rating = 4.8,
                    reviewCount = "950",
                    imageUrl = "ic_cat_eats",
                    badgeLabel = "WARM BAKERY ALERT",
                    statusBadge = "Oven: 4m ago",
                    tagsRaw = "~3 mins line|||Peak Crispness: 12 min window|||Fresh Batch Pulled",
                    proTip = "Ask for canela powder at the counter and pair with a single bica.",
                    contributor = "@patricia_bakes",
                    upvotes = 310,
                    isSaved = false,
                    locationName = "Castelo Slope",
                    latitude = 38.7135,
                    longitude = -9.1328
                ),
                ScoutSpotEntity(
                    id = "spot_3",
                    title = "Convento do Carmo Ruins & Gothic Arches",
                    description = "The roofless convent destroyed by the 1755 earthquake stands open to the sky as a haunting monument. Walk the quiet side gate next to Santa Justa.",
                    category = "history",
                    distance = "0.4 km away",
                    walkMinutes = 6,
                    rating = 4.9,
                    reviewCount = "1.4k",
                    imageUrl = "ic_cat_history",
                    badgeLabel = "HISTORY",
                    statusBadge = "Audio Clip 1m30s",
                    tagsRaw = "Gothic Nave|||Skip-Queue Pass|||Audio Tour",
                    proTip = "Wanderer Tip: Skip the 45-minute lift queue by entering the top terrace ramp via Rua Garrett!",
                    contributor = "@pedro_historian",
                    upvotes = 428,
                    isSaved = true,
                    locationName = "Chiado Ridge",
                    latitude = 38.7121,
                    longitude = -9.1402,
                    audioDuration = "90s Quick Lore"
                ),
                ScoutSpotEntity(
                    id = "spot_4",
                    title = "Hidden Tile Tunnel Shortcut — Alfama ↔ Baixa",
                    description = "Unmarked 18th-century covered tile tunnel that connects the lower river road straight to Largo das Portas do Sol without walking up the main tourist tram steps.",
                    category = "shortcut",
                    distance = "520m walk",
                    walkMinutes = 7,
                    rating = 4.9,
                    reviewCount = "88",
                    imageUrl = "ic_cat_shortcut",
                    badgeLabel = "COMMUNITY GEM",
                    statusBadge = "94% Verified",
                    tagsRaw = "Cuts 12 mins uphill|||Cobblestone Pass|||Secret Pass",
                    proTip = "Look for the iron street lamp with the brass falcon crest.",
                    contributor = "@ana_traveler",
                    contributorAvatar = null,
                    upvotes = 88,
                    isSaved = false,
                    isDiscovered = true,
                    discoveredTimestamp = System.currentTimeMillis() - 3600000L * 6,
                    userNotes = "Shaved 12 minutes off the walk uphill to Portas do Sol. Ancient tiles still intact.",
                    locationName = "Portas do Sol Passage",
                    latitude = 38.7126,
                    longitude = -9.1301
                ),
                ScoutSpotEntity(
                    id = "spot_5",
                    title = "Manteigaria Fábrica — Warm Tart Counter",
                    description = "The marble counter bell just chimed for batch #14. Stand at the standing bar, pair with bica espresso, and sprinkle extra canela.",
                    category = "eats",
                    distance = "0.8 km away",
                    walkMinutes = 11,
                    rating = 4.8,
                    reviewCount = "1.2k",
                    imageUrl = "ic_cat_eats",
                    badgeLabel = "STREET EATS · WARM BATCH",
                    statusBadge = "Fresh out oven 3m ago",
                    tagsRaw = "Crisp Crust|||No Line right now|||€1.40 / piece",
                    proTip = "Watch the bakers stretch the puff pastry through the glass kitchen wall.",
                    contributor = "@lisbon_foodie",
                    upvotes = 1200,
                    isSaved = false,
                    locationName = "Praça Luís de Camões",
                    latitude = 38.7107,
                    longitude = -9.1438
                ),
                ScoutSpotEntity(
                    id = "spot_6",
                    title = "Senhora do Monte Highest Overlook",
                    description = "The highest natural terrace in Lisbon. Pack local Vinho Verde from Graça market and find stone ledge seating before sunset peak.",
                    category = "sights",
                    distance = "1.2 km away",
                    walkMinutes = 18,
                    rating = 4.9,
                    reviewCount = "3.2k",
                    imageUrl = "ic_cat_sights",
                    badgeLabel = "PANORAMIC SUNSET",
                    statusBadge = "Golden Hour 19:42",
                    tagsRaw = "Golden Hour 19:42|||Free Access|||Busker Live",
                    proTip = "Arrive 30 minutes before golden hour to snag the stone ledge next to the small chapel.",
                    contributor = "@sunset_scout",
                    upvotes = 894,
                    isSaved = true,
                    locationName = "Graça Peak",
                    latitude = 38.7192,
                    longitude = -9.1328
                ),
                ScoutSpotEntity(
                    id = "spot_7",
                    title = "São Jorge Citadel & Moat Garden",
                    description = "Behind the main ticket line lies a lush pine moat trail where peacocks roam freely and castle stonework remains undisturbed by crowds.",
                    category = "history",
                    distance = "1.6 km away",
                    walkMinutes = 22,
                    rating = 4.8,
                    reviewCount = "2.1k",
                    imageUrl = "ic_cat_history",
                    badgeLabel = "HIDDEN HISTORY",
                    statusBadge = "Pine Shaded Trail",
                    tagsRaw = "Secret Garden|||Medieval Walls|||Pine Shade",
                    proTip = "Take the perimeter trench path instead of the main courtyard.",
                    contributor = "@history_buff",
                    upvotes = 614,
                    isSaved = false,
                    locationName = "Castle Dry Moat",
                    latitude = 38.7139,
                    longitude = -9.1335
                ),
                ScoutSpotEntity(
                    id = "spot_8",
                    title = "Miradouro Secreto do Beco do Forno",
                    description = "Secluded stone bench balcony looking down onto terracotta rooftops with climbing bougainvillea and gentle ocean breeze.",
                    category = "sights",
                    distance = "310m walk",
                    walkMinutes = 4,
                    rating = 5.0,
                    reviewCount = "Offline Draft",
                    imageUrl = "ic_cat_secret",
                    badgeLabel = "OFFLINE SCOUT DRAFT",
                    statusBadge = "Pending Auto-Sync",
                    tagsRaw = "Offline Queued|||GPS Cached: ±3m|||Photo Stored Locally",
                    proTip = "Auto-sync will transmit high-res capture upon Wi-Fi reconnect.",
                    contributor = "@you (Offline)",
                    upvotes = 1,
                    isSaved = false,
                    isOfflineDraft = true,
                    locationName = "Chiado Alleyway",
                    latitude = 38.7112,
                    longitude = -9.1423
                )
            )
            dao.insertSpots(initialSpots)
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
    }
}
