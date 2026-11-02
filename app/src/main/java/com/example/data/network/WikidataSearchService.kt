package com.example.data.network

import android.util.Log
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Specialized service using Retrofit 2 and WikidataSparqlApi to query the Wikidata Knowledge Graph
 * SPARQL endpoint by GPS coordinates and category filters.
 *
 * Enriches ScoutSpots with multi-language historical descriptions and linked 1200px HD
 * Wikimedia Commons media URLs (P18 property).
 */
class WikidataSearchService(
    private val api: WikidataSparqlApi = WikidataRetrofitClient.api
) {

    companion object {
        private const val TAG = "WikidataSearchService"
    }

    /**
     * Queries Wikidata SPARQL Endpoint via Retrofit for cultural heritage, monuments, viewpoints,
     * eateries, and architectural landmarks near a given GPS coordinate.
     */
    suspend fun fetchNearbyWikidataSpots(
        lat: Double,
        lon: Double,
        category: String = "all",
        radiusKm: Double = 5.0,
        primaryLang: String = "en"
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        val spots = mutableListOf<ScoutSpotEntity>()
        try {
            val p31Filter = getP31CategoryFilter(category)
            val langChain = buildLangChain(primaryLang)

            // SPARQL query string fetching nearby items, multi-lang labels, descriptions, coordinates & P18 media
            val sparql = """
                SELECT ?item ?itemLabel ?itemDescription ?coord ?image WHERE {
                  SERVICE wikibase:around {
                    ?item wdt:P625 ?coord .
                    bd:serviceParam wikibase:center "Point($lon $lat)"^^geo:wktLiteral .
                    bd:serviceParam wikibase:radius "$radiusKm" .
                  }
                  $p31Filter
                  OPTIONAL { ?item wdt:P18 ?image . }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "$langChain". }
                } LIMIT 15
            """.trimIndent()

            // Execute Retrofit call
            val response = api.querySparql(sparqlQuery = sparql)
            val bindings = response.results?.bindings ?: return@withContext emptyList()

            for (i in bindings.indices) {
                val binding = bindings[i]

                // Extract Item QID or Label
                val itemUri = binding["item"]?.value ?: ""
                val qid = itemUri.substringAfterLast("/")
                val label = binding["itemLabel"]?.value ?: continue

                // Ignore raw QID placeholders (e.g., Q12345 when no label matches)
                if (label.startsWith("Q") && label.substring(1).all { it.isDigit() }) continue

                // Extract Multi-Language Description
                val rawDesc = binding["itemDescription"]?.value ?: ""
                val description = if (rawDesc.isNotBlank()) {
                    rawDesc.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    "Historical & cultural landmark cataloged on Wikidata Knowledge Graph ($qid)."
                }

                // Extract Coordinates
                val coordStr = binding["coord"]?.value ?: ""
                var spotLat = lat
                var spotLon = lon
                if (coordStr.contains("Point(")) {
                    val parts = coordStr.substringAfter("Point(").substringBefore(")").split(" ")
                    if (parts.size == 2) {
                        spotLon = parts[0].toDoubleOrNull() ?: lon
                        spotLat = parts[1].toDoubleOrNull() ?: lat
                    }
                }

                // Extract and Resolve P18 Image URL to 1200px HD
                val imageUrlRaw = binding["image"]?.value ?: ""
                val rawFileName = if (imageUrlRaw.isNotBlank()) {
                    val file = imageUrlRaw.substringAfterLast("/")
                    if (file.startsWith("File:")) file else "File:$file"
                } else ""

                val imageUrlHd = if (imageUrlRaw.isNotBlank()) {
                    val fileName = imageUrlRaw.substringAfterLast("/")
                    resolveWikimediaCommonsSpecialFilePath(fileName, 1200)
                } else ""

                val mappedCategory = mapWikidataCategory(category, description)

                spots.add(
                    ScoutSpotEntity(
                        id = "wikidata_$qid",
                        title = label,
                        description = description,
                        category = mappedCategory,
                        distance = "Nearby",
                        walkMinutes = 7,
                        rating = 4.8,
                        reviewCount = 110,
                        imageUrl = imageUrlHd,
                        badgeLabel = "Wikidata",
                        statusBadge = "Wikidata Heritage",
                        tagsRaw = "wikidata|||$qid|||$rawFileName|||$mappedCategory",
                        proTip = "Wikidata QID: $qid. Wikimedia Commons: $rawFileName. $description",
                        latitude = spotLat,
                        longitude = spotLon,
                        locationName = "Wikidata Landmark"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "WikidataSearchService Retrofit SPARQL query error", e)
        }
        spots
    }

    /**
     * Resolves a Wikimedia Commons P18 image filename into a 1200px HD resolution URL.
     */
    private fun resolveWikimediaCommonsSpecialFilePath(fileName: String, targetWidth: Int = 1200): String {
        if (fileName.isBlank()) return ""
        val cleaned = fileName.removePrefix("File:").removePrefix("Image:").trim()
        val encoded = try {
            URLEncoder.encode(cleaned.replace(" ", "_"), StandardCharsets.UTF_8.toString())
                .replace("+", "%20")
        } catch (e: Exception) {
            cleaned.replace(" ", "_")
        }
        return "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=$targetWidth"
    }

    /**
     * Builds localized language priority string for Wikidata SPARQL queries.
     * Order: Primary User Lang -> English -> Portuguese -> Spanish -> French -> German -> Japanese
     */
    private fun buildLangChain(primaryLang: String): String {
        val langs = mutableListOf<String>()
        if (primaryLang.isNotBlank() && primaryLang.length <= 5) {
            langs.add(primaryLang.lowercase())
        }
        listOf("en", "pt", "es", "fr", "de", "ja").forEach {
            if (!langs.contains(it)) langs.add(it)
        }
        return langs.joinToString(",")
    }

    /**
     * Returns Wikidata P31 instance-of SPARQL filter statement for target category.
     */
    private fun getP31CategoryFilter(category: String): String {
        return when (category) {
            "history" -> "?item wdt:P31/wdt:P279* ?type . FILTER(?type IN (wd:Q9259, wd:Q83440, wd:Q570116, wd:Q4989906, wd:Q11910, wd:Q1081138))"
            "eats" -> "?item wdt:P31/wdt:P279* ?type . FILTER(?type IN (wd:Q11707, wd:Q213205, wd:Q43229, wd:Q102804))"
            "sights" -> "?item wdt:P31/wdt:P279* ?type . FILTER(?type IN (wd:Q570116, wd:Q22698, wd:Q33506, wd:Q4989906, wd:Q1081138))"
            else -> "?item wdt:P31/wdt:P279* ?type . FILTER(?type IN (wd:Q9259, wd:Q83440, wd:Q570116, wd:Q11707, wd:Q22698, wd:Q4989906, wd:Q11910))"
        }
    }

    private fun mapWikidataCategory(category: String, description: String): String {
        if (category != "all") return category
        val descLower = description.lowercase()
        return when {
            descLower.contains("restaurant") || descLower.contains("cafe") || descLower.contains("bakery") -> "eats"
            descLower.contains("monument") || descLower.contains("church") || descLower.contains("castle") || descLower.contains("palace") || descLower.contains("museum") -> "history"
            else -> "sights"
        }
    }
}
