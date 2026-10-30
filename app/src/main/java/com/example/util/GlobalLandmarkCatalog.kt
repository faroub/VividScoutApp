package com.example.util

import java.text.Normalizer
import java.util.Locale

/**
 * Global Landmark Catalog featuring iconic points of interest across major world regions
 * (Europe, Asia, Americas, Africa, Oceania, Middle East) with verified Wikimedia Commons photography,
 * plus contextual category matching for global exploration.
 */
object GlobalLandmarkCatalog {

    data class LandmarkEntry(
        val canonicalName: String,
        val aliases: List<String>,
        val country: String,
        val category: String,
        val imageUrl: String,
        val attribution: String
    )

    val GLOBAL_LANDMARKS: List<LandmarkEntry> = listOf(
        // Paris
        LandmarkEntry(
            canonicalName = "Eiffel Tower",
            aliases = listOf("Tour Eiffel", "Eiffel", "Champ de Mars"),
            country = "France",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a8/Eiffel_Tower_from_the_Tour_Montparnasse3.jpg/800px-Eiffel_Tower_from_the_Tour_Montparnasse3.jpg",
            attribution = "Wikimedia Commons / Eiffel Tower"
        ),
        LandmarkEntry(
            canonicalName = "Louvre Museum",
            aliases = listOf("Musée du Louvre", "Louvre", "Pyramide du Louvre"),
            country = "France",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/Louvre_Museum_Wikimedia_Commons.jpg/800px-Louvre_Museum_Wikimedia_Commons.jpg",
            attribution = "Wikimedia Commons / Louvre Museum"
        ),
        LandmarkEntry(
            canonicalName = "Sacré-Cœur",
            aliases = listOf("Basilique du Sacré-Cœur", "Sacre Coeur", "Montmartre"),
            country = "France",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e6/Sacr%C3%A9-C%C5%93ur_de_Montmartre_July_2018.jpg/800px-Sacr%C3%A9-C%C5%93ur_de_Montmartre_July_2018.jpg",
            attribution = "Wikimedia Commons / Sacré-Cœur Montmartre"
        ),

        // Tokyo & Japan
        LandmarkEntry(
            canonicalName = "Shibuya Crossing",
            aliases = listOf("Shibuya Scramble", "Shibuya", "Shibuya Sky"),
            country = "Japan",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/13/Shibuya_Crossing_Tokyo.jpg/800px-Shibuya_Crossing_Tokyo.jpg",
            attribution = "Wikimedia Commons / Shibuya Crossing"
        ),
        LandmarkEntry(
            canonicalName = "Sensō-ji",
            aliases = listOf("Sensoji", "Asakusa Kannon Temple", "Asakusa Shrine"),
            country = "Japan",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/Sens%C5%8D-ji_01.jpg/800px-Sens%C5%8D-ji_01.jpg",
            attribution = "Wikimedia Commons / Sensō-ji Asakusa"
        ),
        LandmarkEntry(
            canonicalName = "Fushimi Inari Shrine",
            aliases = listOf("Fushimi Inari Taisha", "Torii Gates", "Fushimi Inari"),
            country = "Japan",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Fushimi_Inari_Taisha_Torii_Path.jpg/800px-Fushimi_Inari_Taisha_Torii_Path.jpg",
            attribution = "Wikimedia Commons / Fushimi Inari"
        ),

        // New York
        LandmarkEntry(
            canonicalName = "Central Park",
            aliases = listOf("Bethesda Terrace", "The Ramble", "Central Park Mall"),
            country = "United States",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Central_Park_NYC_2021.jpg/800px-Central_Park_NYC_2021.jpg",
            attribution = "Wikimedia Commons / Central Park NYC"
        ),
        LandmarkEntry(
            canonicalName = "Statue of Liberty",
            aliases = listOf("Lady Liberty", "Liberty Island"),
            country = "United States",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Lady_Liberty_under_a_blue_sky_%28cropped%29.jpg/800px-Lady_Liberty_under_a_blue_sky_%28cropped%29.jpg",
            attribution = "Wikimedia Commons / Statue of Liberty"
        ),

        // London
        LandmarkEntry(
            canonicalName = "Big Ben",
            aliases = listOf("Elizabeth Tower", "Palace of Westminster", "Houses of Parliament"),
            country = "United Kingdom",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/Clock_Tower_-_Big_Ben_-_London.jpg/800px-Clock_Tower_-_Big_Ben_-_London.jpg",
            attribution = "Wikimedia Commons / Big Ben London"
        ),
        LandmarkEntry(
            canonicalName = "Tower Bridge",
            aliases = listOf("London Tower Bridge", "Thames Bridge"),
            country = "United Kingdom",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/Tower_Bridge_from_Shad_Thames_London_2020.jpg/800px-Tower_Bridge_from_Shad_Thames_London_2020.jpg",
            attribution = "Wikimedia Commons / Tower Bridge"
        ),

        // Rome
        LandmarkEntry(
            canonicalName = "Colosseum",
            aliases = listOf("Anfiteatro Flavio", "Colosseo"),
            country = "Italy",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/Colosseum_in_Rome-April_2007-1-_Unprocessed.jpg/800px-Colosseum_in_Rome-April_2007-1-_Unprocessed.jpg",
            attribution = "Wikimedia Commons / Colosseum Rome"
        ),
        LandmarkEntry(
            canonicalName = "Trevi Fountain",
            aliases = listOf("Fontana di Trevi", "Trevi"),
            country = "Italy",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/Trevi_Fountain%2C_Rome_2021.jpg/800px-Trevi_Fountain%2C_Rome_2021.jpg",
            attribution = "Wikimedia Commons / Trevi Fountain"
        ),

        // Sydney
        LandmarkEntry(
            canonicalName = "Sydney Opera House",
            aliases = listOf("Opera House Sydney", "Bennelong Point"),
            country = "Australia",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a0/Sydney_Opera_House_Sails.jpg/800px-Sydney_Opera_House_Sails.jpg",
            attribution = "Wikimedia Commons / Sydney Opera House"
        ),

        // Rio
        LandmarkEntry(
            canonicalName = "Christ the Redeemer",
            aliases = listOf("Cristo Redentor", "Corcovado"),
            country = "Brazil",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Cristo_Redentor_Rio_de_Janeiro_4.jpg/800px-Cristo_Redentor_Rio_de_Janeiro_4.jpg",
            attribution = "Wikimedia Commons / Christ the Redeemer"
        ),

        // Cairo
        LandmarkEntry(
            canonicalName = "Pyramids of Giza",
            aliases = listOf("Great Pyramid", "Sphinx", "Giza Plateau"),
            country = "Egypt",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/Kheops-Pyramid.jpg/800px-Kheops-Pyramid.jpg",
            attribution = "Wikimedia Commons / Pyramids of Giza"
        ),

        // Barcelona
        LandmarkEntry(
            canonicalName = "Sagrada Família",
            aliases = listOf("Sagrada Familia", "Basílica de la Sagrada Família"),
            country = "Spain",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ee/Sagrada_Familia_01.jpg/800px-Sagrada_Familia_01.jpg",
            attribution = "Wikimedia Commons / Sagrada Família"
        )
    )

    private val CATEGORY_PHOTOS = mapOf(
        "sights" to "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=800&q=80",
        "eats" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=800&q=80",
        "history" to "https://images.unsplash.com/photo-1555881400-74d7acaacd81?auto=format&fit=crop&w=800&q=80",
        "shortcut" to "https://images.unsplash.com/photo-1558151091-a74247990204?auto=format&fit=crop&w=800&q=80"
    )

    fun findLandmark(name: String?): LandmarkEntry? {
        if (name.isNullOrBlank()) return null
        val norm = normalize(name)
        return GLOBAL_LANDMARKS.find { entry ->
            normalize(entry.canonicalName) == norm || entry.aliases.any { normalize(it) == norm || norm.contains(normalize(it)) }
        }
    }

    fun resolveGlobalContextualPhoto(
        title: String?,
        category: String?,
        districtOrLocation: String? = null
    ): Pair<String, String> {
        val normTitle = normalize(title ?: "")

        // 1. Keyword matching across global languages
        when {
            normTitle.contains("bakery") || normTitle.contains("boulangerie") || normTitle.contains("cafe") || normTitle.contains("espresso") || normTitle.contains("coffee") || normTitle.contains("pastry") -> {
                return Pair(
                    "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=800&q=80",
                    "Global Cafe & Artisan Bakery"
                )
            }
            normTitle.contains("viewpoint") || normTitle.contains("overlook") || normTitle.contains("belvedere") || normTitle.contains("panorama") || normTitle.contains("miradouro") -> {
                return Pair(
                    "https://images.unsplash.com/photo-1513688275127-1be5a8359473?auto=format&fit=crop&w=800&q=80",
                    "Panoramic Viewpoint Vista"
                )
            }
            normTitle.contains("temple") || normTitle.contains("shrine") || normTitle.contains("church") || normTitle.contains("cathedral") || normTitle.contains("monastery") || normTitle.contains("sanctuary") -> {
                return Pair(
                    "https://images.unsplash.com/photo-1555881400-74d7acaacd81?auto=format&fit=crop&w=800&q=80",
                    "Historic Cultural Heritage"
                )
            }
            normTitle.contains("park") || normTitle.contains("garden") || normTitle.contains("botanical") || normTitle.contains("square") || normTitle.contains("piazza") -> {
                return Pair(
                    "https://images.unsplash.com/photo-1519331379826-f10be5486c6f?auto=format&fit=crop&w=800&q=80",
                    "Urban Park & Civic Green"
                )
            }
            normTitle.contains("stairs") || normTitle.contains("steps") || normTitle.contains("shortcut") || normTitle.contains("alley") -> {
                return Pair(
                    "https://images.unsplash.com/photo-1558151091-a74247990204?auto=format&fit=crop&w=800&q=80",
                    "Pedestrian Alleyway Pass"
                )
            }
        }

        // 2. Category fallback
        val cat = category?.lowercase(Locale.ROOT)?.trim() ?: "sights"
        val photoUrl = CATEGORY_PHOTOS[cat] ?: CATEGORY_PHOTOS["sights"]!!
        return Pair(photoUrl, "Worldwide Destination Scene")
    }

    private fun normalize(input: String): String {
        val temp = Normalizer.normalize(input, Normalizer.Form.NFD)
        return temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}
