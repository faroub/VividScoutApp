package com.example.util

interface LandmarkCatalog {
    val isSupported: Boolean
    val landmarks: List<LisbonLandmarkCatalog.LandmarkEntry>
    fun findLandmark(title: String?): LisbonLandmarkCatalog.LandmarkEntry?
    fun resolveContextualPhoto(title: String?, category: String?, districtOrLocation: String? = null): Pair<String, String>
}

object EmptyLandmarkCatalog : LandmarkCatalog {
    override val isSupported: Boolean = false
    override val landmarks: List<LisbonLandmarkCatalog.LandmarkEntry> = emptyList()
    override fun findLandmark(title: String?): LisbonLandmarkCatalog.LandmarkEntry? = null
    override fun resolveContextualPhoto(title: String?, category: String?, districtOrLocation: String?): Pair<String, String> {
        return Pair("ic_cat_default", "General Exploration")
    }
}
