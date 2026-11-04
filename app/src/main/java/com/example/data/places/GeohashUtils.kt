package com.example.data.places

import kotlin.math.cos

/**
 * Geohash utility for 5-character precision spatial tile keys (~5 km x 5 km bounding tiles)
 * and neighboring tile coverage calculations.
 */
object GeohashUtils {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /**
     * Encodes latitude and longitude into a geohash string of specified precision (default 5 characters ~5km tile).
     */
    fun encode(lat: Double, lng: Double, precision: Int = 5): String {
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        var isEven = true
        var bit = 0
        var ch = 0
        val geohash = StringBuilder()

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lngMin + lngMax) / 2
                if (lng > mid) {
                    ch = ch or (1 shl (4 - bit))
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat > mid) {
                    ch = ch or (1 shl (4 - bit))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }

        return geohash.toString()
    }

    /**
     * Calculates the center geohash and its 8 surrounding neighboring tiles
     * to ensure search radius tile-edge coverage across bounding borders.
     */
    fun getCoveringTiles(lat: Double, lng: Double, radiusMeters: Int = 3000): List<String> {
        val center = encode(lat, lng, precision = 5)
        val deltaLat = (radiusMeters / 111000.0).coerceAtLeast(0.03) // Approx degrees for radius
        val deltaLng = (radiusMeters / (111000.0 * cos(Math.toRadians(lat)))).coerceAtLeast(0.03)

        val tiles = mutableSetOf<String>()
        tiles.add(center)

        // Compute 8 directional neighbor coordinate offsets
        val latOffsets = listOf(-deltaLat, 0.0, deltaLat)
        val lngOffsets = listOf(-deltaLng, 0.0, deltaLng)

        for (dLat in latOffsets) {
            for (dLng in lngOffsets) {
                val sampleLat = (lat + dLat).coerceIn(-90.0, 90.0)
                val sampleLng = (lng + dLng).coerceIn(-180.0, 180.0)
                tiles.add(encode(sampleLat, sampleLng, precision = 5))
            }
        }

        return tiles.toList()
    }
}
