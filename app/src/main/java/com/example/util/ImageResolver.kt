package com.example.util

import androidx.annotation.DrawableRes
import com.example.R

/**
 * Utility to resolve high-resolution local category drawables, offline resource mappings,
 * authentic Wikimedia/Wikipedia photos, and fallbacks for Scout Lisbon spots.
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
     * Intercepts placeholder vector names or generic repeated stock photos and resolves
     * authentic Wikimedia Commons imagery from the Lisbon Landmark Catalog.
     */
    fun resolveImageModel(imageUrl: String?, category: String? = null, title: String? = null): Any {
        val rawUrl = imageUrl?.trim()

        // 1. If URL is missing, or is a generic stock photo / vector placeholder name, attempt authentic resolution
        if (rawUrl.isNullOrBlank() || LocationPhotoResolver.isGenericOrStockPhoto(rawUrl)) {
            val resolved = LocationPhotoResolver.resolvePhotoSynchronous(title, category)
            if (resolved.url.isNotBlank()) {
                return resolved.url
            }
        }

        if (rawUrl.isNullOrBlank()) {
            return getDrawableResForCategory(category)
        }

        // 2. Handle android.resource:// or local file/content URIs
        if (rawUrl.startsWith("android.resource://") || rawUrl.startsWith("content://") || rawUrl.startsWith("file://")) {
            return rawUrl
        }

        // 3. Handle valid HTTP / HTTPS URLs
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }

        // 4. Handle string-based drawable names
        if (rawUrl.startsWith("ic_cat_") || rawUrl.startsWith("img_lisbon_")) {
            return when (rawUrl) {
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

    /**
     * Returns an authentic attribution label for the photo displayed.
     */
    fun getAttribution(title: String?, imageUrl: String?, category: String? = null): String {
        val landmark = LisbonLandmarkCatalog.findLandmark(title)
        if (landmark != null) {
            return landmark.attribution
        }
        val url = imageUrl?.trim() ?: ""
        return when {
            url.contains("upload.wikimedia.org") || url.contains("commons.wikimedia.org") -> "Wikimedia Commons"
            url.contains("wikipedia.org") -> "Wikipedia"
            url.contains("openstreetmap") -> "OpenStreetMap Contributor"
            url.startsWith("http") -> "Verified Photo Feed"
            else -> "Local Scout Archive"
        }
    }
}
