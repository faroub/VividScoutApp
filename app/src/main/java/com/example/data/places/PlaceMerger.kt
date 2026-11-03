package com.example.data.places

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Merges and deduplicates PlaceCandidate instances from multiple external data sources.
 *
 * Deduplication Criteria:
 * 1. Geodesic distance <= 50 meters.
 * 2. Normalized name similarity >= 0.85 using Jaro-Winkler string distance algorithm.
 *
 * Algorithm Choice Rationale:
 * Jaro-Winkler string similarity is chosen over Levenshtein because place and venue names
 * are short strings where prefix matches and minor character transpositions or missing punctuation
 * (e.g. "Miradouro de Santa Luzia" vs "Miradouro Santa Luzia") occur frequently. Jaro-Winkler
 * applies a prefix scaling factor that significantly improves matching precision for venue names.
 */
object PlaceMerger {

    const val MAX_MERGE_DISTANCE_METERS = 50.0
    const val MIN_NAME_SIMILARITY = 0.85

    fun mergeCandidates(candidates: List<PlaceCandidate>): List<MergedPlace> {
        if (candidates.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<PlaceCandidate>>()

        for (candidate in candidates) {
            var matchedCluster: MutableList<PlaceCandidate>? = null

            for (cluster in clusters) {
                if (cluster.any { existing -> isSamePlace(candidate, existing) }) {
                    matchedCluster = cluster
                    break
                }
            }

            if (matchedCluster != null) {
                matchedCluster.add(candidate)
            } else {
                clusters.add(mutableListOf(candidate))
            }
        }

        return clusters.map { buildMergedPlace(it) }
    }

    private fun isSamePlace(a: PlaceCandidate, b: PlaceCandidate): Boolean {
        val distMeters = calculateDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        if (distMeters > MAX_MERGE_DISTANCE_METERS) return false

        val normA = normalizeName(a.name)
        val normB = normalizeName(b.name)
        val similarity = calculateJaroWinklerSimilarity(normA, normB)

        return similarity >= MIN_NAME_SIMILARITY
    }

    private fun buildMergedPlace(cluster: List<PlaceCandidate>): MergedPlace {
        // Prefer Wikidata candidates for richest title & description, otherwise pick longest description
        val wikidataCandidate = cluster.find { it.sourceName.contains("Wikidata", ignoreCase = true) }
        val bestNameCandidate = wikidataCandidate
            ?: cluster.maxByOrNull { it.name.length }
            ?: cluster.first()

        val bestDescCandidate = wikidataCandidate?.takeIf { it.description.length > 30 }
            ?: cluster.maxByOrNull { it.description.length }
            ?: cluster.first()

        val bestImageCandidate = cluster.find { !it.imageUrl.isNullOrBlank() }
        val bestSourceUrlCandidate = cluster.find { !it.sourceUrl.isNullOrBlank() }
        val bestLicenseCandidate = cluster.find { !it.license.isNullOrBlank() }

        val attributions = cluster.map { c ->
            PlaceSourceAttribution(
                sourceName = c.sourceName,
                sourceId = c.sourceId,
                sourceUrl = c.sourceUrl,
                license = c.license
            )
        }.distinctBy { "${it.sourceName}_${it.sourceId}" }

        val avgLat = cluster.map { it.latitude }.average()
        val avgLng = cluster.map { it.longitude }.average()

        return MergedPlace(
            primaryName = bestNameCandidate.name,
            primaryDescription = bestDescCandidate.description,
            latitude = avgLat,
            longitude = avgLng,
            category = bestNameCandidate.category,
            imageUrl = bestImageCandidate?.imageUrl,
            sourceUrl = bestSourceUrlCandidate?.sourceUrl,
            license = bestLicenseCandidate?.license,
            languageCode = bestNameCandidate.languageCode,
            sources = attributions
        )
    }

    /**
     * Normalizes place names by lowercasing and stripping punctuation and excess whitespace.
     */
    fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Calculates Jaro-Winkler string similarity between two strings (returns 0.0 to 1.0).
     */
    fun calculateJaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val jaro = calculateJaroSimilarity(s1, s2)
        val prefixLength = getCommonPrefixLength(s1, s2).coerceAtMost(4)
        val p = 0.1 // Standard Winkler scaling factor

        return jaro + (prefixLength * p * (1.0 - jaro))
    }

    private fun calculateJaroSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        val matchDistance = max(len1, len2) / 2 - 1

        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        var transpositions = 0

        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)

            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0
    }

    private fun getCommonPrefixLength(s1: String, s2: String): Int {
        val minLen = min(s1.length, s2.length)
        for (i in 0 until minLen) {
            if (s1[i] != s2[i]) return i
        }
        return minLen
    }

    /**
     * Haversine formula for calculating geodesic distance in meters between two coordinates.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
