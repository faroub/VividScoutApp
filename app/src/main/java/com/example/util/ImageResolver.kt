package com.example.util

import androidx.annotation.DrawableRes
import com.example.R

data class ImageTelemetry(
    val rawUrl: String,
    val provider: String,
    val resolutionType: String,
    val isVerifiedLandmark: Boolean
)

/**
 * Utility to resolve high-resolution local category drawables, offline resource mappings,
 * authentic Wikimedia/Wikipedia photos, and fallbacks for Scout spots worldwide.
 */
object ImageResolver {

    @DrawableRes
    fun getDrawableResForCategory(category: String?): Int {
        val cat = category?.lowercase()?.trim() ?: ""
        return when {
            cat.contains("sight") || cat.contains("viewpoint") || cat.contains("scenic") || cat.contains("miradouro") || cat.contains("touristic") -> R.drawable.ic_cat_sights
            cat.contains("eat") || cat.contains("bakery") || cat.contains("food") || cat.contains("tart") || cat.contains("cafe") || cat.contains("restaurant") -> R.drawable.ic_cat_eats
            cat.contains("histor") || cat.contains("monument") || cat.contains("castle") || cat.contains("convent") || cat.contains("gothic") || cat.contains("museum") -> R.drawable.ic_cat_history
            cat.contains("shortcut") || cat.contains("tunnel") || cat.contains("passage") || cat.contains("alley") -> R.drawable.ic_cat_shortcut
            cat.contains("secret") || cat.contains("draft") || cat.contains("community") || cat.contains("gems") -> R.drawable.ic_cat_secret
            else -> R.drawable.ic_cat_default
        }
    }

    /**
     * Resolves the Coil model (URL, Uri, or Drawable resource ID) for a spot.
     * Intercepts placeholder vector names or generic repeated stock photos and resolves
     * authentic Wikimedia Commons / Unsplash imagery with spot ID hashing for varied visuals.
     */
    fun resolveImageModel(imageUrl: String?, category: String? = null, title: String? = null, spotId: String? = null): Any {
        val rawUrl = imageUrl?.trim()

        // 1. If URL is missing, or is a generic stock photo / vector placeholder name, attempt authentic resolution
        if (rawUrl.isNullOrBlank() || LocationPhotoResolver.isGenericOrStockPhoto(rawUrl)) {
            val resolved = LocationPhotoResolver.resolvePhotoSynchronous(title = title, category = category, spotId = spotId)
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
     * Returns full diagnostic image telemetry including raw URL, provider name, and resolution type.
     */
    fun getImageTelemetry(imageUrl: String?, category: String? = null, title: String? = null, spotId: String? = null): ImageTelemetry {
        val resolvedModel = resolveImageModel(imageUrl, category, title, spotId)
        val finalUrl = resolvedModel.toString()

        val globalLandmark = GlobalLandmarkCatalog.findLandmark(title)
        if (globalLandmark != null) {
            return ImageTelemetry(
                rawUrl = finalUrl,
                provider = globalLandmark.attribution,
                resolutionType = "Global Catalog Registry",
                isVerifiedLandmark = true
            )
        }

        val lisbonLandmark = LisbonLandmarkCatalog.findLandmark(title)
        if (lisbonLandmark != null) {
            return ImageTelemetry(
                rawUrl = finalUrl,
                provider = lisbonLandmark.attribution,
                resolutionType = "Lisbon Catalog Registry",
                isVerifiedLandmark = true
            )
        }

        val providerName = when {
            finalUrl.contains("upload.wikimedia.org") || finalUrl.contains("commons.wikimedia.org") -> "Wikimedia Commons REST API"
            finalUrl.contains("wikipedia.org") -> "Wikipedia Summary REST API"
            finalUrl.contains("unsplash.com") -> "Dynamic Category Photo Pool"
            finalUrl.contains("openstreetmap") -> "OpenStreetMap Node Tag"
            finalUrl.startsWith("android.resource") || finalUrl.startsWith("ic_cat_") -> "Android System Vector Asset"
            else -> "Local Scout Cache"
        }

        val resolutionType = when {
            finalUrl.contains("wikimedia.org") || finalUrl.contains("wikipedia.org") -> "Verified REST API"
            finalUrl.contains("unsplash.com") -> "Category Hash Fallback"
            else -> "Resource Asset"
        }

        return ImageTelemetry(
            rawUrl = finalUrl,
            provider = providerName,
            resolutionType = resolutionType,
            isVerifiedLandmark = finalUrl.contains("wikimedia") || finalUrl.contains("wikipedia")
        )
    }

    /**
     * Returns an authentic attribution label for the photo displayed.
     */
    fun getAttribution(title: String?, imageUrl: String?, category: String? = null): String {
        return getImageTelemetry(imageUrl, category, title).provider
    }
}
