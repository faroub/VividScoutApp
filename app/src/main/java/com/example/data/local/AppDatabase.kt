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
    entities = [ScoutSpotEntity::class, DiscoveredGemEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoutDao(): ScoutDao
    abstract fun discoveredGemDao(): DiscoveredGemDao

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
                    .fallbackToDestructiveMigration()
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
                    }
                }
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC3yq57UPilhAgxFkvUZQ5DXMV0nLjnY1lj6srOo8k71oIJOj9esabTT7DxOYTau3HaO_RITybss_bGFcHfxzDzJKCE4rM1X9OhUPA_yHSe2X5b3hn5WrZKMq9gAELDIHmFyAZK6l5EBzsBR-USkjbvbvtjaddKnGUSbapgxmMo93rxnCTXBhVu_uLpq3eisrgGHIRqHgvN25OFa5sBKZ70slSvRtWb1s0WI5KcEe-8x8OzKZ4aEKLj",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDPCK5oapFoy00NJ1b3mIU0vUn2Gnrk1awb3Jvl7kyGVnyA4wBvHgjmdRb1tqk2Ok6_GcFB5mibaV-scj50JK3m0jew3pZok2LB27QElGEzIN5lg4nTU-dyqYXNkkgkJQ0e1xB35HgmVVw1zL2hgYJv_rTaQDmfuTfmYGtQIqejW_-O9s7fEOTtrq48qvwsh7V5mH6PbnPMRzdRInngGIFWL0a69w6iex0qFfeCsklaE8HhuOmIcDah",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBPtZx47YTIT9FDFZdINpU_-pVnF79RzoKvF3OePU02L7RoEUwGaCLDNrJcDicykBnIB3t2O2Lv7vgeN5u2fIhfvytDkad9QuPKpiAckNYsH4inim64vxuBeQRo4sbXxnqGsNgPGGJSQ8Xm25r_EIKWkm9Cs9WgMVMf7KBxJPRlAeFDrCaMCocdhIJ5HwRAC1Y6oPOupNturZDoSezwCKp71MDbaNFtCircAnOqwJREtCcaL1XNmrT5",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDYYMaNLMTo5oXwUSLoYVEVxcb5NfdLPTYuRmJNjzB95cBREyxMjMcbiygmDP30SrPdRwckFobYF4K0DHMJ0s3NJNqPGyXDiXsXDquy2oeFhm_4kmK5ExLEUenaQdtxdgGt4WuUmP-9ZDpvivln0-aX4OCh9CHhjjZWUUevRFlRR0zBXHkIWAqZLXe5tBWZwMqIUN0Al_I12L8FJib5c7Tmqy0Ej42_1fzpsQ1CjZK5wVJhbXkAwSay",
                    badgeLabel = "COMMUNITY GEM",
                    statusBadge = "94% Verified",
                    tagsRaw = "Cuts 12 mins uphill|||Cobblestone Pass|||Secret Pass",
                    proTip = "Look for the iron street lamp with the brass falcon crest.",
                    contributor = "@ana_traveler",
                    contributorAvatar = "https://lh3.googleusercontent.com/aida-public/AB6AXuDjhSwx8_tEIeIp1FmafMomZgm7wK0L_DWQB3f28iVgrWWutERlumrWrcb9yhpS5Pd7brXb9Sz_EBjGE8OKZIvGP3lXrtOFTGQfWGUMfbyitWMg9fwxXyRb8bE-9x3XAsrWey9LaSoUpEZC-3V4qb3oXsEvmkUNcDJoPhr6abcNuPKMLzwvpdWe0T8GG9B_a3RZR_s7uScpEPtVFz63C3_3tBwf1GO5wSLEoMPYs0n4KJZLCLcwnUNU",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCX7TovRy3XzH1uE9mvV91IY27SQePR3ZGLyTtwu9FiDg0QKewNNsH0s0_EZMKDHdM-E4Ns0ScaM_HPa-QVhDFyiLv_eulNvUig3WBoHn6DXjAXHT6n8fSTSJ4Fq25RX2bbn6vib3x7PtMU2RvqyV5hCn2thWxNZ17xrecUQu9BFvD4DxXi8PJfiebziZxGtFHKtMj1wWVIQ840aI8CrMEnVIcrYmTXgEHtM5JN6M7omxkXpErrsv4R",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBOhbli2-YQxZAhAPDPhNvvTacfDkHOHHHzQoSlWoSPPOHy_ZDNBDjTTuEdX9cjFNrNERYhx_w6rg8NBlpYMd7Mx0TZEFrK_urq1XrKKQOiAMNOebbvbbEeDIwRnyCf_UCHd-QBPUdSu3IFuPA_7g_sN5oDoZgTNM2PVXMnTjbVKAgoFQNO1HiameuZDjSjchyYwnEC5z6N40mG_zkdCPyS4PMJVtYs1-ha5ry67HhAMl9dhxYoDJsL",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBXvw73czX-hYaqEdjVA0sZz4T7dGTpYzWIZit1Fr-Vo903wE-HlvLUdxxAelwkXZnuu-Lj4YoT4zDuK26SH_ePqSzHkIzYXbyEXfdF7ZH9Qeo--q7qFarCHAZwejijWy0-ofYKCdAGeOUJfMVXBYNKThmn_tZ01S5VqWs9eKpmCii6Z0OcOZ1hI-NBINU9C4SHx0Vt66Jzs254Of15B-2C5rDxnuVpb-_3CAb33bk6FkBNM6Scay18",
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
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB7xEP9h28BmLIl7Blc9HlVd6pFvRCzWVE0iDC6Ca1oPEDNOlydMy8IbMDvHPNp5_0uUlGCZu5Cw6Bm39F_KkbvLPOmcgrXHSU-a4h7PVnRNgle66SM05K7w8EOWeQ5yG6-5OzUUMA-vVU40aqUdOAWyXH1crmx9Vp2tTTAQ1QTNNEvtbnoukMI4v0qHyR2ad0mL70ALOBm16NlvPATzNielDDSV7Rp-pzWtZzzWv8pTeMZBuNBXfjY",
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
    }
}
