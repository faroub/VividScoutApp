package com.example.util

import androidx.annotation.DrawableRes
import com.example.R

/**
 * Utility to resolve high-resolution local category drawables, offline resource mappings,
 * and fallbacks for Scout Lisbon spots and hidden gems.
 */
object ImageResolver {

    @DrawableRes
    fun getDrawableResForCategory(category: String?): Int {
        val cat = category?.lowercase()?.trim() ?: ""
        return when {
            cat.contains("sight") || cat.contains("viewpoint") || cat.contains("scenic") || cat.contains("miradouro") -> R.drawable.ic_cat_sights
            cat.contains("eat") || cat.contains("bakery") || cat.contains("food") || cat.contains("tart") || cat.contains("cafe") -> R.drawable.ic_cat_eats
            cat.contains("histor") || cat.contains("monument") || cat.contains("castle") || cat.contains("convent") || cat.contains("gothic") -> R.drawable.ic_cat_history
            cat.contains("shortcut") || cat.contains("tunnel") || cat.contains("passage") || cat.contains("alley") -> R.drawable.ic_cat_shortcut
            cat.contains("secret") || cat.contains("draft") || cat.contains("community") -> R.drawable.ic_cat_secret
            else -> R.drawable.ic_cat_default
        }
    }

    /**
     * Resolves the Coil model (URL, Uri, or Drawable resource ID) for a spot.
     */
    fun resolveImageModel(imageUrl: String?, category: String? = null, title: String? = null): Any {
        if (imageUrl.isNullOrBlank()) {
            return getDrawableResForCategory(category)
        }

        val url = imageUrl.trim()

        // Handle android.resource:// or local drawable names
        if (url.startsWith("android.resource://") || url.startsWith("content://") || url.startsWith("file://")) {
            return url
        }

        // Handle HTTP / HTTPS URLs
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        // Handle string-based drawable names like "ic_cat_sights"
        if (url.startsWith("ic_cat_") || url.startsWith("img_lisbon_")) {
            return when (url) {
                "ic_cat_sights" -> R.drawable.ic_cat_sights
                "ic_cat_eats" -> R.drawable.ic_cat_eats
                "ic_cat_history" -> R.drawable.ic_cat_history
                "ic_cat_shortcut" -> R.drawable.ic_cat_shortcut
                "ic_cat_secret" -> R.drawable.ic_cat_secret
                else -> getDrawableResForCategory(category)
            }
        }

        return getDrawableResForCategory(category)
    }
}
