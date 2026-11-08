package com.example.data.network

import com.example.data.local.ScoutSpotEntity
import com.example.data.location.LocationTracker
import com.example.data.model.ScoutCategory
import com.example.util.Geo
import com.example.util.SafeLog
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiSpotService"

/**
 * AI-powered Spot Discovery Service leveraging Firebase AI Logic & Gemini 3.8 Flash.
 * Replaces noisy OSM scraping with structured, verified local spots, historical landmarks,
 * dining gems, and cultural events tailored to the user's exact GPS coordinates.
 */
open class GeminiSpotService {

    // Structured JSON Schema definition using Firebase AI Logic Schema API
    private val spotItemSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Official name of the place or landmark"),
            "locationName" to Schema.string(description = "Neighborhood, district, or area name"),
            "category" to Schema.string(description = "One of: touristic, restaurant, historical, events, gems"),
            "description" to Schema.string(description = "Engaging cultural, historical, or practical description"),
            "audioLore" to Schema.string(description = "Immersive 2-3 sentence audio guide narrative"),
            "latitude" to Schema.double(description = "Latitude coordinate close to requested location"),
            "longitude" to Schema.double(description = "Longitude coordinate close to requested location"),
            "distanceText" to Schema.string(description = "Human readable distance string e.g. 300m away"),
            "tags" to Schema.array(
                items = Schema.string(),
                description = "Keywords such as viewpoint, bakery, museum, architecture, local_favorite"
            )
        )
    )

    private val spotListSchema = Schema.obj(
        properties = mapOf(
            "spots" to Schema.array(
                items = spotItemSchema,
                description = "List of discovered real local places and hidden gems"
            )
        )
    )

    private val generativeConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = spotListSchema
    }

    // Primary recommended fast model initialization
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiModelConstants.PRIMARY_MODEL_ID,
            generationConfig = generativeConfig
        )
    }

    // Secondary fallback model if primary model is unavailable
    private val secondaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiModelConstants.SECONDARY_MODEL_ID,
            generationConfig = generativeConfig
        )
    }

    /**
     * Queries Gemini AI for real, authentic, named spots around the specified GPS coordinates.
     * Uses a fast 15-second timeout window and returns verified curated local spots if network is delayed.
     */
    open suspend fun discoverGems(
        userLat: Double,
        userLng: Double,
        category: String = "all",
        radiusMeters: Int = 2000,
        customQuery: String = ""
    ): Result<List<ScoutSpotEntity>> {
        return try {
            val categoryInstruction = if (category.lowercase() == "all") {
                "Include a balanced mix of top tourist attractions, historical landmarks, authentic local restaurants/dining spots, cultural events, and hidden local gems."
            } else {
                "Focus strictly on places matching category '$category'."
            }

            val queryInstruction = if (customQuery.isNotBlank()) {
                "User Search Inquiry / Theme: '$customQuery'. Strongly prioritize verified, famous places, gems, or venues matching this query ($customQuery)."
            } else {
                categoryInstruction
            }

            val prompt = """
                You are VividScout, an expert local travel guide and urban exploration AI.
                Find 4 to 6 authentic, real, verified, named places, historical spots, or dining gems located around coordinates (${Geo.roundForPrompt(userLat)}, ${Geo.roundForPrompt(userLng)}) within a $radiusMeters meter radius.

                Guidelines:
                1. $queryInstruction
                2. Do NOT invent fake or unnamed places. Provide real, famous, or well-known local spots (e.g., real castles, viewpoints, taverns, historic monuments, museums, squares).
                3. Calculate accurate relative coordinates (latitude, longitude) for each spot within approx 2km of (${Geo.roundForPrompt(userLat)}, ${Geo.roundForPrompt(userLng)}).
                4. For each spot, write an engaging 2-3 sentence audio lore narrative suitable for audio guide listening.
                5. Return the result strictly matching the JSON schema.
            """.trimIndent()

            SafeLog.d(TAG, "Discovering gems with ${GeminiModelConstants.PRIMARY_DISPLAY_NAME} (${GeminiModelConstants.PRIMARY_MODEL_ID}) near (${SafeLog.sanitizeCoordinate(userLat)}, ${SafeLog.sanitizeCoordinate(userLng)}), query: '$customQuery', category: $category")

            val jsonText = try {
                val response = withTimeout(15_000L) {
                    model.generateContent(prompt)
                }
                response.text ?: throw IllegalStateException("Gemini returned empty response text.")
            } catch (t: Exception) {
                if (t is CancellationException && t !is TimeoutCancellationException) throw t
                SafeLog.w(TAG, "${GeminiModelConstants.PRIMARY_DISPLAY_NAME} attempt delayed (${t.message}), trying secondary model (${GeminiModelConstants.SECONDARY_DISPLAY_NAME})...")
                val fallbackResponse = withTimeout(10_000L) {
                    secondaryModel.generateContent(prompt)
                }
                fallbackResponse.text ?: throw IllegalStateException("Secondary model returned empty response text.")
            }

            SafeLog.d(TAG, "Gemini response received: ${jsonText.take(200)}...")
            val parsedSpots = parseSpotsJson(jsonText, userLat, userLng, radiusMeters)
            if (parsedSpots.isNotEmpty()) {
                Result.success(parsedSpots)
            } else {
                SafeLog.w(TAG, "Parsed 0 spots from response, generating curated vicinity spots")
                Result.success(generateCuratedVicinitySpots(userLat, userLng, category, radiusMeters))
            }
        } catch (e: TimeoutCancellationException) {
            SafeLog.w(TAG, "Gemini spot discovery timed out gracefully, returning curated verified spots")
            Result.success(generateCuratedVicinitySpots(userLat, userLng, category, radiusMeters))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.w(TAG, "Gemini spot discovery encountered connection issue (${e.message}), returning curated verified spots")
            Result.success(generateCuratedVicinitySpots(userLat, userLng, category, radiusMeters))
        }
    }

    internal fun parseSpotsJson(
        jsonString: String,
        userLat: Double,
        userLng: Double,
        radiusMeters: Int
    ): List<ScoutSpotEntity> {
        val root = JSONObject(jsonString)
        val spotsArray = root.optJSONArray("spots") ?: return emptyList()
        val result = mutableListOf<ScoutSpotEntity>()

        // Enforce strict proportional buffer (requested radius + 15% tolerance) without inflating small radii
        val maxAllowedDistance = radiusMeters.toFloat() * 1.15f

        for (i in 0 until spotsArray.length()) {
            val item = spotsArray.getJSONObject(i)
            if (!item.has("latitude") || !item.has("longitude")) {
                // Drop spot if no coordinates provided by AI
                continue
            }
            val spotLat = item.optDouble("latitude")
            val spotLng = item.optDouble("longitude")
            if (spotLat.isNaN() || spotLng.isNaN() || (spotLat == 0.0 && spotLng == 0.0)) {
                continue
            }
            if (spotLat < -90.0 || spotLat > 90.0 || spotLng < -180.0 || spotLng > 180.0) {
                continue
            }

            // Validate actual geodesic distance against active radius
            val distMeters = LocationTracker.distanceBetween(userLat, userLng, spotLat, spotLng)
            if (distMeters > maxAllowedDistance) {
                SafeLog.w(
                    TAG,
                    "Rejecting AI spot '${item.optString("title")}' at ($spotLat, $spotLng): " +
                    "distance ${distMeters.toInt()}m exceeds radius threshold ${maxAllowedDistance.toInt()}m"
                )
                continue
            }

            val title = item.optString("title", "Local Gem")
            val locationName = item.optString("locationName", "Vicinity")
            val category = item.optString("category", "gems").lowercase()
            val description = item.optString("description", "A discovered local spot.")
            val audioLore = item.optString("audioLore", description)
            val upvotes: Int? = null // AI recommendations do not generate artificial upvote metrics
            // Eliminating artificial ratings: AI discovered spots must have rating = null (reserved for real user reviews)
            val rating: Double? = null
            val reviewCount: Int? = null
            val distanceText = item.optString("distanceText", "350m away")
            
            val tagsArray = item.optJSONArray("tags")
            val tagsList = mutableListOf<String>()
            if (tagsArray != null) {
                for (j in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(j))
                }
            }
            if (tagsList.isEmpty()) {
                tagsList.add(category)
            }

            // Map category names to standard internal canonical categories using ScoutCategory
            val mappedCategory = ScoutCategory.normalize(category)

            val spotId = "gemini_${title.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${spotLat.toString().take(6)}"

            val resolvedPhoto = com.example.util.LocationPhotoResolver.resolvePhotoSynchronous(
                title = title,
                category = mappedCategory,
                districtOrLocation = locationName,
                spotId = spotId
            )

            val entity = ScoutSpotEntity(
                id = spotId,
                title = title,
                description = description,
                category = mappedCategory,
                distance = distanceText,
                walkMinutes = (distanceText.filter { it.isDigit() }.toIntOrNull() ?: 300) / 80 + 1,
                rating = rating,
                reviewCount = reviewCount,
                imageUrl = resolvedPhoto.url,
                badgeLabel = when {
                    resolvedPhoto.isOsmVerified -> "OSM VERIFIED"
                    resolvedPhoto.isCuratedLandmark -> "CURATED LANDMARK"
                    else -> "AI RECOMMENDED"
                },
                statusBadge = when {
                    resolvedPhoto.isOsmVerified -> "OSM Verified"
                    resolvedPhoto.isCuratedLandmark -> "Curated Landmark"
                    else -> "AI Candidate"
                },
                tagsRaw = tagsList.joinToString(","),
                proTip = audioLore,
                upvotes = upvotes,
                isSaved = false,
                isOfflineDraft = false,
                isDiscovered = false,
                locationName = locationName,
                latitude = spotLat,
                longitude = spotLng,
                timestamp = System.currentTimeMillis()
            )
            result.add(entity)
        }

        return result
    }

    /**
     * Generates curated fallback spots from a catalog of real, OSM-verified central
     * Lisbon landmarks. Each landmark keeps its own verified real-world coordinates
     * regardless of the user's location; distances are real Haversine geodesics
     * ([LocationTracker.distanceBetween]) and walking minutes follow the same
     * 80 m/min model used elsewhere in the app. A landmark is only emitted when it
     * lies within [radiusMeters] of the user, so users outside the catalog's actual
     * coverage (central Lisbon) receive an empty result and the repository can fall
     * back to real provider discoveries instead of invented nearby landmarks.
     */
    fun generateCuratedVicinitySpots(
        userLat: Double,
        userLng: Double,
        category: String = "all",
        radiusMeters: Int = 2000
    ): List<ScoutSpotEntity> {
        data class CuratedSpotBlueprint(
            val title: String,
            val district: String,
            val category: String,
            val description: String,
            val audioLore: String,
            val badgeLabel: String,
            val rating: Double? = null,
            val latitude: Double,
            val longitude: Double,
            val imageUrl: String,
            val tags: String
        )

        // Invalid request coordinates (NaN, out of range, null island) yield no spots.
        if (userLat.isNaN() || userLng.isNaN() ||
            userLat !in -90.0..90.0 || userLng !in -180.0..180.0 ||
            (userLat == 0.0 && userLng == 0.0)
        ) {
            return emptyList()
        }

        // Verified catalog: every landmark, coordinate, and type confirmed against
        // OpenStreetMap (Nominatim) — real places at their real-world locations.
        // The catalog covers central Lisbon only.
        val catalog = listOf(
            CuratedSpotBlueprint(
                title = "Miradouro de Santa Luzia",
                district = "Alfama Terraces",
                category = "touristic",
                description = "Romantic bougainvillea-framed terrace overlooking the red terracotta roofs of Alfama down to the blue Tagus estuary.",
                audioLore = "Legend says 18th-century sailors would look back up at these painted blue azulejo panels depicting Praça do Comércio before departing into the Atlantic.",
                badgeLabel = "ICONIC VIEWPOINT",
                rating = null,
                latitude = 38.7117382,
                longitude = -9.1301295,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/MiradouroSantaLuzia.JPG/800px-MiradouroSantaLuzia.JPG",
                tags = "viewpoint,terrace,azulejos,romantic,alfama"
            ),
            CuratedSpotBlueprint(
                title = "Pastelaria Manteigaria — Chiado",
                district = "Praça Luís de Camões",
                category = "restaurant",
                description = "Artisan bakery where pastel de nata custard tarts are hand-rolled and baked fresh continuously before your eyes.",
                audioLore = "Listen closely for the bronze bell ringing over Camões square—every single chime signals a fresh hot tray coming right out of the 300-degree oven.",
                badgeLabel = "MUST-TRY FOOD GEM",
                rating = null,
                latitude = 38.7107638,
                longitude = -9.1440621,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/24/Pastel_de_Nata_Manteigaria.jpg/800px-Pastel_de_Nata_Manteigaria.jpg",
                tags = "bakery,pastry,local_favorite,cinnamon,chiado"
            ),
            CuratedSpotBlueprint(
                title = "Castelo de São Jorge Ramparts",
                district = "Santa Maria Maior",
                category = "historical",
                description = "11th-century Moorish citadel perched atop the highest hill of the historic center offering 360-degree panoramic rampart walks.",
                audioLore = "The ancient stone pine trees here cast deep shade over battlements that have withstood earthquakes, sieges, and eight centuries of royal intrigue.",
                badgeLabel = "HISTORIC FORTRESS",
                rating = null,
                latitude = 38.7139258,
                longitude = -9.1334830,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg/800px-Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg",
                tags = "castle,fortress,history,panoramic,moorish"
            ),
            CuratedSpotBlueprint(
                title = "Elevador da Bica Funicular",
                district = "Bica / Misericórdia",
                category = "touristic",
                description = "Charming yellow funicular operating since 1892, climbing the steep, lantern-lit cobblestones between Rua de São Paulo and Calhariz.",
                audioLore = "Designed by Raoul Mesnier du Ponsard, this twin-cable carriage is immortalized in vintage photographs with laundry fluttering above from wrought-iron balconies.",
                badgeLabel = "LIVING HERITAGE",
                rating = null,
                latitude = 38.7097584,
                longitude = -9.1462053,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/Elevador_da_Bica_01.jpg/800px-Elevador_da_Bica_01.jpg",
                tags = "funicular,tram,cobblestone,street,bica"
            ),
            CuratedSpotBlueprint(
                title = "Convento do Carmo Ruins",
                district = "Largo do Carmo",
                category = "historical",
                description = "Roofless Gothic church preserved as a hauntingly beautiful open-air memorial following the Great 1755 Lisbon Earthquake.",
                audioLore = "Look straight up through the pointed rib-vault arches to see open sky—the monument serves as a silent testament to survival and rebirth.",
                badgeLabel = "GOTHIC MONUMENT",
                rating = null,
                latitude = 38.7120060,
                longitude = -9.1405862,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Convento_do_Carmo_Lisbon.jpg/800px-Convento_do_Carmo_Lisbon.jpg",
                tags = "monument,gothic,history,museum,chiado"
            ),
            CuratedSpotBlueprint(
                title = "Taberna da Rua das Flores",
                district = "Chiado / Baixa",
                category = "restaurant",
                description = "Cozy chalkboard-menu tavern championing seasonal Portuguese petiscos, cured cheeses, and fresh Atlantic seafood.",
                audioLore = "There are no reservations here: patrons gather under the wooden awning sipping chilled vinho verde while chatting with locals.",
                badgeLabel = "LOCAL TAVERN",
                rating = null,
                latitude = 38.7101395,
                longitude = -9.1435869,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Taberna_tradicional_Lisboa_petiscos.jpg/800px-Taberna_tradicional_Lisboa_petiscos.jpg",
                tags = "dining,seafood,wine,cozy,tavern"
            )
        )

        // Category filtering normalizes BOTH the requested category and each blueprint's
        // category through ScoutCategory (the app-wide canonical IDs) so aliases match:
        // restaurant/bakery/cafe/dining -> eats, historical/monument/castle -> history,
        // touristic/viewpoint/attraction -> sights. "all" keeps the full catalog. A
        // category with no matching curated records returns an empty list — the full
        // catalog is never silently served for an unmatched category.
        val categoryFiltered = catalog.filter { ScoutCategory.matches(category, it.category) }

        val effectiveRadius = radiusMeters.coerceAtLeast(1)

        return categoryFiltered.mapNotNull { spot ->
            val distMeters = LocationTracker.distanceBetween(userLat, userLng, spot.latitude, spot.longitude)
            if (distMeters.isNaN() || distMeters < 0f || distMeters > effectiveRadius) {
                return@mapNotNull null
            }
            val spotId = "curated_${spot.title.lowercase().replace(Regex("[^a-z0-9]"), "_")}"

            distMeters to ScoutSpotEntity(
                id = spotId,
                title = spot.title,
                description = spot.description,
                category = ScoutCategory.normalize(spot.category),
                distance = "${distMeters.toInt()}m away",
                walkMinutes = (distMeters / 80f).toInt().coerceAtLeast(1),
                rating = spot.rating,
                reviewCount = null,
                imageUrl = spot.imageUrl,
                badgeLabel = spot.badgeLabel,
                statusBadge = "CURATED FALLBACK",
                tagsRaw = spot.tags,
                proTip = spot.audioLore,
                upvotes = null,
                isSaved = false,
                isOfflineDraft = false,
                isDiscovered = false,
                locationName = spot.district,
                latitude = spot.latitude,
                longitude = spot.longitude,
                timestamp = System.currentTimeMillis()
            )
        }.sortedBy { it.first }.map { it.second }
    }
}
