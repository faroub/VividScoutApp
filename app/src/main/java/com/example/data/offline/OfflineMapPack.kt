package com.example.data.offline

import androidx.annotation.Keep

@Keep
data class OfflineMapPack(
    val id: String,
    val cityName: String,
    val country: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int = 3000,
    val estimatedSizeBytes: Long = 4_200_000L,
    val actualSizeBytes: Long = 0L,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedAt: Long = 0L
) {
    val displaySize: String
        get() {
            val bytes = if (actualSizeBytes > 0L) actualSizeBytes else estimatedSizeBytes
            val mb = bytes / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}
