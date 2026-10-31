package com.example.data.offline

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.repository.ScoutGemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfflineMapManager(
    private val context: Context,
    private val gemRepository: ScoutGemRepository? = null
) {
    private val TAG = "OfflineMapManager"

    private val offlineDir: File by lazy {
        File(context.filesDir, "offline_map_packs").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val defaultPresetPacks = listOf(
        OfflineMapPack(
            id = "dresden_saxony",
            cityName = "Dresden & Saxony",
            country = "Germany",
            lat = 51.0504,
            lng = 13.7373,
            radiusMeters = 3500,
            estimatedSizeBytes = 4_350_000L
        ),
        OfflineMapPack(
            id = "lisbon_cascais",
            cityName = "Lisbon & Cascais",
            country = "Portugal",
            lat = 38.7112,
            lng = -9.1423,
            radiusMeters = 3500,
            estimatedSizeBytes = 3_900_000L
        ),
        OfflineMapPack(
            id = "berlin_mitte",
            cityName = "Berlin Mitte",
            country = "Germany",
            lat = 52.5200,
            lng = 13.4050,
            radiusMeters = 3500,
            estimatedSizeBytes = 5_100_000L
        ),
        OfflineMapPack(
            id = "paris_centre",
            cityName = "Paris Centre",
            country = "France",
            lat = 48.8566,
            lng = 2.3522,
            radiusMeters = 3500,
            estimatedSizeBytes = 4_800_000L
        )
    )

    private val _packsState = MutableStateFlow<List<OfflineMapPack>>(emptyList())
    val packsState: StateFlow<List<OfflineMapPack>> = _packsState.asStateFlow()

    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    init {
        refreshPacksState()
    }

    /**
     * Reads internal storage to refresh pack download statuses and file sizes.
     */
    fun refreshPacksState(currentGpsLat: Double? = null, currentGpsLng: Double? = null) {
        val packs = defaultPresetPacks.toMutableList()

        // Append custom GPS city pack if coordinates are provided
        if (currentGpsLat != null && currentGpsLng != null) {
            val customId = "custom_gps_pack"
            val existingIndex = packs.indexOfFirst { it.id == customId }
            val customPack = OfflineMapPack(
                id = customId,
                cityName = "Current GPS Location",
                country = String.format(java.util.Locale.US, "%.3f° N, %.3f° E", currentGpsLat, currentGpsLng),
                lat = currentGpsLat,
                lng = currentGpsLng,
                radiusMeters = 3000,
                estimatedSizeBytes = 3_500_000L
            )
            if (existingIndex >= 0) {
                packs[existingIndex] = customPack
            } else {
                packs.add(0, customPack)
            }
        }

        var totalSize = 0L
        val updatedList = packs.map { pack ->
            val packFile = File(offlineDir, "${pack.id}.json")
            if (packFile.exists()) {
                val size = packFile.length()
                totalSize += size
                pack.copy(
                    isDownloaded = true,
                    isDownloading = false,
                    actualSizeBytes = size,
                    downloadProgress = 100,
                    downloadedAt = packFile.lastModified()
                )
            } else {
                pack
            }
        }

        _packsState.value = updatedList
        _totalStorageBytes.value = totalSize
    }

    /**
     * Downloads an offline map pack into internal storage (`context.filesDir/offline_map_packs/<packId>.json`)
     * with chunked progress updates.
     */
    suspend fun downloadPack(packId: String, currentGpsLat: Double? = null, currentGpsLng: Double? = null) = withContext(Dispatchers.IO) {
        val currentPacks = _packsState.value
        val pack = currentPacks.find { it.id == packId } ?: return@withContext

        // Mark pack as downloading
        _packsState.value = _packsState.value.map {
            if (it.id == packId) it.copy(isDownloading = true, downloadProgress = 5) else it
        }

        try {
            // Step 1: Query Overpass gems if gemRepository is provided
            val targetLat = if (packId == "custom_gps_pack" && currentGpsLat != null) currentGpsLat else pack.lat
            val targetLng = if (packId == "custom_gps_pack" && currentGpsLng != null) currentGpsLng else pack.lng

            // Progress steps 10% to 50%
            for (p in 10..50 step 10) {
                delay(120)
                updateProgress(packId, p)
            }

            // Sync gems from Overpass into Room
            gemRepository?.syncLiveOsmGems(targetLat, targetLng, pack.radiusMeters)

            // Progress steps 60% to 90%
            for (p in 60..90 step 10) {
                delay(100)
                updateProgress(packId, p)
            }

            // Write vector package JSON payload to internal storage file
            val packJsonObject = JSONObject().apply {
                put("id", pack.id)
                put("cityName", pack.cityName)
                put("country", pack.country)
                put("lat", targetLat)
                put("lng", targetLng)
                put("radiusMeters", pack.radiusMeters)
                put("downloadedAt", System.currentTimeMillis())
                put("version", 1)

                // Synthesize vector grid contour layers for offline canvas rendering
                val layersArray = JSONArray()
                val contoursObject = JSONObject().apply {
                    put("name", "street_contours")
                    put("baseScale", 26000)
                    put("centerLat", targetLat)
                    put("centerLng", targetLng)
                }
                layersArray.put(contoursObject)
                put("layers", layersArray)
            }

            val file = File(offlineDir, "${packId}.json")
            file.writeText(packJsonObject.toString(2))

            delay(150)
            updateProgress(packId, 100)

            // Refresh state
            refreshPacksState(currentGpsLat, currentGpsLng)
            Log.i(TAG, "Successfully downloaded offline map pack ${pack.id} (${file.length()} bytes) to internal storage.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download map pack $packId", e)
            _packsState.value = _packsState.value.map {
                if (it.id == packId) it.copy(isDownloading = false, downloadProgress = 0) else it
            }
        }
    }

    /**
     * Deletes an offline map pack from internal storage (`context.filesDir/offline_map_packs/<packId>.json`).
     */
    suspend fun deletePack(packId: String, currentGpsLat: Double? = null, currentGpsLng: Double? = null) = withContext(Dispatchers.IO) {
        val file = File(offlineDir, "${packId}.json")
        if (file.exists()) {
            val deleted = file.delete()
            Log.i(TAG, "Deleted offline map pack $packId file: $deleted")
        }
        refreshPacksState(currentGpsLat, currentGpsLng)
    }

    private fun updateProgress(packId: String, progress: Int) {
        val pack = _packsState.value.find { it.id == packId }
        _packsState.value = _packsState.value.map {
            if (it.id == packId) it.copy(downloadProgress = progress) else it
        }

        // Post notification progress update if service is running
        try {
            val cityName = pack?.cityName ?: "Vector Map"
            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(
                context,
                OfflineMapDownloadService.CHANNEL_ID
            )
                .setContentTitle("Downloading $cityName Vector Map")
                .setContentText("$progress% completed")
                .setSmallIcon(com.example.R.mipmap.ic_launcher)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()

            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(OfflineMapDownloadService.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Safe recovery: Notification posting failed (e.g. permission missing), log telemetry without breaking background download
            android.util.Log.w("OfflineMapManager", "Failed to post download status notification", e)
        }
    }
}
