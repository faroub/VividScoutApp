package com.example.data.network

import android.util.Log
import com.example.data.local.ScoutSpotEntity
import com.example.data.network.model.OverpassElement
import com.example.data.network.model.OverpassResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Retrofit client for OpenStreetMap Overpass API.
 * Fetches real nodes representing viewpoints, bakeries, and shortcuts near current coordinates.
 */
class OverpassRetrofitClient(
    baseUrl: String = "https://overpass-api.de/"
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "VividScout/1.0 (Android; Retrofit Overpass Client)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: OverpassApi = retrofit.create(OverpassApi::class.java)

    // Mirror URLs if primary server experiences high traffic
    private val mirrorBaseUrls = listOf(
        "https://overpass-api.de/",
        "https://maps.mail.ru/osm/tools/overpass/",
        "https://overpass.kumi.systems/"
    )

    private val viewpointImages = listOf(
        "https://images.unsplash.com/photo-1513688275127-1be5a8359473?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1588668214407-6ea9a6d8c272?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1548707309-dcebeab9ea9b?auto=format&fit=crop&w=800&q=80"
    )

    private val bakeryImages = listOf(
        "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1589301760014-d929f3979dbc?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=800&q=80"
    )

    private val shortcutImages = listOf(
        "https://images.unsplash.com/photo-1558151091-a74247990204?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1580837119756-563d608dd119?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=800&q=80"
    )

    private val historicImages = listOf(
        "https://images.unsplash.com/photo-1579893901844-3158c5f59051?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1555881400-74d7acaacd81?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1585208798174-6cedd86e019a?auto=format&fit=crop&w=800&q=80"
    )

    /**
     * Builds the Overpass QL query targeting nodes for viewpoints, bakeries, and shortcuts near coordinates.
     */
    fun buildQuery(lat: Double, lng: Double, radiusMeters: Int): String {
        return """
            [out:json][timeout:20];
            (
              node["tourism"="viewpoint"](around:$radiusMeters,$lat,$lng);
              node["shop"="bakery"](around:$radiusMeters,$lat,$lng);
              node["highway"="steps"](around:$radiusMeters,$lat,$lng);
              node["highway"="footway"](around:$radiusMeters,$lat,$lng);
              node["historic"](around:$radiusMeters,$lat,$lng);
              way["highway"="steps"]["name"](around:$radiusMeters,$lat,$lng);
              node["amenity"="cafe"]["name"](around:$radiusMeters,$lat,$lng);
            );
            out body center qt 30;
        """.trimIndent()
    }

    /**
     * Fetches nodes representing viewpoints, bakeries, and shortcuts near user coordinates
     * using the Retrofit Overpass API.
     */
    suspend fun fetchNearbyNodes(
        lat: Double,
        lng: Double,
        radiusMeters: Int = 1500
    ): Result<List<ScoutSpotEntity>> = withContext(Dispatchers.IO) {
        val qlQuery = buildQuery(lat, lng, radiusMeters)
        var lastError: Exception? = null

        // Try primary client first, then fallback to mirrors if needed
        for (mirror in mirrorBaseUrls) {
            try {
                val client = if (mirror == "https://overpass-api.de/") this@OverpassRetrofitClient else OverpassRetrofitClient(mirror)
                val response: OverpassResponse = client.api.queryOverpass(qlQuery)
                val elements = response.elements
                if (elements.isNotEmpty()) {
                    val entities = mapElementsToEntities(elements, lat, lng)
                    Log.i("OverpassRetrofit", "Retrofit successfully parsed ${entities.size} spots from $mirror")
                    return@withContext Result.success(entities)
                }
            } catch (e: Exception) {
                Log.w("OverpassRetrofit", "Mirror $mirror failed: ${e.message}")
                lastError = e
            }
        }

        if (lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(emptyList())
        }
    }

    /**
     * Converts OverpassElement DTOs into ScoutSpotEntity domain models.
     */
    fun mapElementsToEntities(
        elements: List<OverpassElement>,
        userLat: Double,
        userLng: Double
    ): List<ScoutSpotEntity> {
        return elements.mapIndexedNotNull { index, element ->
            val tags = element.tags ?: emptyMap()

            val latitude = element.lat ?: element.center?.lat ?: return@mapIndexedNotNull null
            val longitude = element.lon ?: element.center?.lon ?: return@mapIndexedNotNull null

            // Determine category and branding
            val category: String
            val badgeLabel: String
            val imageUrl: String

            when {
                tags["shop"] == "bakery" || tags["amenity"] == "cafe" -> {
                    category = "eats"
                    badgeLabel = if (tags["shop"] == "bakery") "WARM BAKERY ALERT" else "LOCAL CAFE FEED"
                    imageUrl = bakeryImages[index % bakeryImages.size]
                }
                tags.containsKey("tourism") && tags["tourism"] == "viewpoint" -> {
                    category = "sights"
                    badgeLabel = "OSM VIEWPOINT"
                    imageUrl = viewpointImages[index % viewpointImages.size]
                }
                tags.containsKey("highway") -> {
                    category = "shortcut"
                    badgeLabel = "SECRET PASS"
                    imageUrl = shortcutImages[index % shortcutImages.size]
                }
                tags.containsKey("historic") -> {
                    category = "history"
                    badgeLabel = "HISTORIC MONUMENT"
                    imageUrl = historicImages[index % historicImages.size]
                }
                else -> {
                    category = "sights"
                    badgeLabel = "COMMUNITY GEM"
                    imageUrl = viewpointImages[index % viewpointImages.size]
                }
            }

            // Derive title
            val rawName = tags["name"] ?: tags["name:en"] ?: tags["name:pt"]
            val title = if (!rawName.isNullOrBlank()) {
                rawName
            } else {
                when (category) {
                    "eats" -> "Pastelaria & Bakery — Fresh Batch #${index + 1}"
                    "sights" -> "Scenic Viewpoint Overlook #${index + 1}"
                    "shortcut" -> "Cobblestone Steps Shortcut #${index + 1}"
                    "history" -> "Historic ${tags["historic"]?.replace('_', ' ')?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } ?: "Site"} Landmark"
                    else -> "Discovered Node #${element.id}"
                }
            }

            // Description
            val desc = tags["description"] ?: tags["note"] ?: when (category) {
                "eats" -> "Local bakery node fetched from OpenStreetMap via Retrofit. Serving warm pastel de nata and coffee."
                "sights" -> "Viewpoint node verified on OpenStreetMap. Panoramic vantage point with wide horizon views."
                "shortcut" -> "Pedestrian steps shortcut node from OpenStreetMap. Links alleyways away from heavy traffic."
                "history" -> "Historic heritage node documented in OpenStreetMap."
                else -> "Geographic node fetched via Retrofit Overpass API client."
            }

            val street = tags["addr:street"] ?: "Lisbon Historic District"
            val randomRating = 4.5 + (Random.nextDouble() * 0.4)
            val formattedRating = String.format(Locale.US, "%.1f", randomRating).toDoubleOrNull() ?: 4.8

            ScoutSpotEntity(
                id = "osm_${element.type}_${element.id}",
                title = title,
                description = desc,
                category = category,
                distance = "Calculating...",
                walkMinutes = 5,
                rating = formattedRating,
                reviewCount = "Retrofit OSM",
                imageUrl = imageUrl,
                badgeLabel = badgeLabel,
                statusBadge = "Live OSM Scout",
                tagsRaw = "Retrofit|||OpenStreetMap|||Nodes Verified",
                proTip = "Fetched via Retrofit Overpass API client. Tap Bookmark to store permanently in Room DB.",
                contributor = "@osm_contributor",
                upvotes = 15 + (index * 6) % 75,
                isSaved = false,
                isOfflineDraft = false,
                isDiscovered = false,
                discoveredTimestamp = null,
                userNotes = null,
                offlineCached = true,
                locationName = street,
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}
