package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.data.local.ScoutSpotEntity

/**
 * Explore screen Map Overlay Layers:
 * - Historical: Monuments, Castles, Museums, Heritage
 * - Culinary: Bakeries, Cafes, Traditional Eats, Dining
 * - Shortcut: Secret Passages, Stairways, Alleys, Hidden Gems
 */
enum class ExploreMapLayer(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val categoryId: String,
    val color: Color
) {
    HISTORICAL(
        id = "historical",
        displayName = "Historical",
        subtitle = "Monuments & Heritage",
        categoryId = "history",
        color = Color(0xFFA855F7) // Radiant Purple
    ),
    CULINARY(
        id = "culinary",
        displayName = "Culinary",
        subtitle = "Bakeries & Traditional Eats",
        categoryId = "eats",
        color = Color(0xFFF59E0B) // Amber Glow
    ),
    SHORTCUT(
        id = "shortcut",
        displayName = "Shortcut",
        subtitle = "Secret Alleys & Stairways",
        categoryId = "shortcut",
        color = Color(0xFF06B6D4) // Vibrant Cyan Teal
    );

    companion object {
        val ALL_LAYERS: Set<ExploreMapLayer> = entries.toSet()

        /**
         * Returns the layer that matches the given spot category, or null if none.
         */
        fun fromSpotCategory(category: String): ExploreMapLayer? {
            val normalized = ScoutCategory.normalize(category)
            return entries.firstOrNull { it.categoryId == normalized }
        }

        /**
         * Checks if a given spot matches any of the active layers.
         */
        fun isSpotLayerActive(spot: ScoutSpotEntity, activeLayers: Set<ExploreMapLayer>): Boolean {
            val layer = fromSpotCategory(spot.category) ?: return false
            return activeLayers.contains(layer)
        }
    }
}
