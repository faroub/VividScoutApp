package com.example.data.network

import android.util.Log
import com.example.data.local.ScoutSpotEntity
import com.example.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to query UNESCO World Heritage Sites spatial coordinates, official designation details,
 * and historical significance criteria.
 */
class UnescoHeritageService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "UnescoHeritageService"
        private const val USER_AGENT = "VividScoutApp/1.0 (Android; UNESCO World Heritage Resolver; contact: scout@example.com)"

        val CURATED_UNESCO_SITES = listOf(
            ScoutSpotEntity(
                id = "unesco_belem_tower",
                title = "Torre de Belém & Jerónimos Monastery",
                description = "Official UNESCO World Cultural Heritage Site (Inscribed 1983). Iconic Manueline architectural masterpiece built on the Tagus River.",
                category = "history",
                distance = "12 min walk",
                walkMinutes = 12,
                rating = null,
                reviewCount = null,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Torre_Bel%C3%A9m_November_2016-1a.jpg/1200px-Torre_Bel%C3%A9m_November_2016-1a.jpg",
                badgeLabel = "UNESCO Heritage",
                statusBadge = "UNESCO Site #263",
                tagsRaw = "unesco|||heritage|||manueline|||history",
                proTip = "Official UNESCO World Heritage Site #263 (Criteria iii, vi). Best viewed at sunset.",
                latitude = 38.6916,
                longitude = -9.2160,
                locationName = "Belém, Lisbon"
            ),
            ScoutSpotEntity(
                id = "unesco_sintra_cultural",
                title = "Cultural Landscape of Sintra",
                description = "Official UNESCO World Heritage Site (Inscribed 1995). Romanticist architecture nestled within lush mountain pine forests.",
                category = "history",
                distance = "25 km",
                walkMinutes = 30,
                rating = null,
                reviewCount = null,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/Pena_National_Palace_Sintra.jpg/1200px-Pena_National_Palace_Sintra.jpg",
                badgeLabel = "UNESCO Heritage",
                statusBadge = "UNESCO Site #723",
                tagsRaw = "unesco|||sintra|||palace|||romanticism",
                proTip = "Official UNESCO World Heritage Site #723 (Criteria ii, iv, v).",
                latitude = 38.7876,
                longitude = -9.3906,
                locationName = "Sintra Mountain"
            ),
            ScoutSpotEntity(
                id = "unesco_historic_porto",
                title = "Historic Centre of Oporto & Luiz I Bridge",
                description = "Official UNESCO World Cultural Heritage Site (Inscribed 1996). Urban landscape stretching over 2,000 years of Portuguese heritage.",
                category = "history",
                distance = "Porto Region",
                walkMinutes = 15,
                rating = null,
                reviewCount = null,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Porto_Ribeira_2019.jpg/1200px-Porto_Ribeira_2019.jpg",
                badgeLabel = "UNESCO Heritage",
                statusBadge = "UNESCO Site #755",
                tagsRaw = "unesco|||porto|||douro|||monument",
                proTip = "Official UNESCO World Heritage Site #755 (Criterion iv).",
                latitude = 41.1400,
                longitude = -8.6110,
                locationName = "Oporto Historic District"
            )
        )
    }

    /**
     * Fetches UNESCO World Heritage Monuments near given coordinates.
     */
    suspend fun fetchNearbyUnescoSites(
        lat: Double,
        lon: Double,
        radiusKm: Double = 30.0
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        CURATED_UNESCO_SITES.filter { spot ->
            val distMeters = Geo.calculateDistanceMeters(lat, lon, spot.latitude, spot.longitude)
            !distMeters.isNaN() && distMeters <= (radiusKm * 1000.0)
        }
    }
}
