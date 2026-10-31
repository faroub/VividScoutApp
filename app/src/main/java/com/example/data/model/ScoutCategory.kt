package com.example.data.model

/**
 * Standardized category identifiers used consistently across Gemini AI,
 * OpenStreetMap, Room Database, ViewModels, and Compose UI.
 *
 * Canonical Category IDs:
 * - "all": All Categories
 * - "sights": Sightseeing, Viewpoints, Landmarks & Parks
 * - "eats": Bakeries, Restaurants, Cafes & Dining
 * - "history": Historic Sites, Monuments, Castles & Museums
 * - "shortcut": Secret Passages, Alleys, Shortcuts & Hidden Gems
 */
enum class ScoutCategory(val id: String, val displayName: String) {
    ALL("all", "All Nearby"),
    SIGHTS("sights", "Sights"),
    EATS("eats", "Eats"),
    HISTORY("history", "History"),
    SHORTCUT("shortcut", "Shortcuts");

    companion object {
        /**
         * Normalizes any raw category string (from Gemini AI, Overpass OSM, or UI)
         * to the canonical internal category ID ("sights", "eats", "history", "shortcut").
         */
        fun normalize(rawCategory: String?): String {
            if (rawCategory.isNullOrBlank()) return SIGHTS.id
            val lower = rawCategory.trim().lowercase()
            return when {
                lower in listOf("all", "*") -> ALL.id
                lower in listOf("sights", "touristic", "tourist", "sightseeing", "viewpoint", "park", "attraction", "sights") -> SIGHTS.id
                lower in listOf("eats", "restaurant", "dining", "food", "cafe", "bakery", "tavern", "pub", "eats") -> EATS.id
                lower in listOf("history", "historical", "monument", "castle", "museum", "gothic", "ruins", "church", "history") -> HISTORY.id
                lower in listOf("shortcut", "secret", "gems", "passage", "alley", "steps", "pedestrian", "shortcut") -> SHORTCUT.id
                else -> SIGHTS.id
            }
        }

        /**
         * Determines if a spot category matches the requested filter category,
         * performing canonical normalization so AI-generated categories match UI filters.
         */
        fun matches(filterCategory: String, spotCategory: String): Boolean {
            val normalizedFilter = normalize(filterCategory)
            if (normalizedFilter == ALL.id) return true
            val normalizedSpot = normalize(spotCategory)
            return normalizedFilter == normalizedSpot
        }
    }
}
