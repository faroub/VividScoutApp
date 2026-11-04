package com.example.data.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMergerUnicodeTest {

    private fun candidate(id: String, name: String, lat: Double = 38.7112, lng: Double = -9.1423): PlaceCandidate {
        return PlaceCandidate(
            sourceName = "TestProvider",
            sourceId = id,
            name = name,
            latitude = lat,
            longitude = lng,
            category = "sights"
        )
    }

    @Test
    fun normalizeName_portugueseAccents_decomposesAndStripsDiacritics() {
        val norm = PlaceMerger.normalizeName("Miradouro de Santa Luzia, Lisboa!")
        assertEquals("miradouro de santa luzia lisboa", norm)
    }

    @Test
    fun normalizeName_germanUmlauts_preservesGermanCharactersAndNormalizes() {
        val norm = PlaceMerger.normalizeName("München Hauptbahnhof")
        assertEquals("munchen hauptbahnhof", norm)
    }

    @Test
    fun normalizeName_arabicScript_preservesArabicLetters() {
        val norm = PlaceMerger.normalizeName("متحف اللوفر (باريس)")
        assertEquals("متحف اللوفر باريس", norm)
    }

    @Test
    fun normalizeName_cyrillicScript_preservesCyrillicLetters() {
        val norm = PlaceMerger.normalizeName("Собор Святого Марка.")
        assertEquals("собор святого марка", norm)
    }

    @Test
    fun normalizeName_greekScript_decomposesAccentsAndPreservesGreekLetters() {
        val norm1 = PlaceMerger.normalizeName("Ακρόπολη Αθηνών")
        val norm2 = PlaceMerger.normalizeName("Ακροπολη Αθηνων")
        assertEquals(norm1, norm2)
        assertEquals("ακροπολη αθηνων", norm1)
    }

    @Test
    fun normalizeName_chineseJapaneseScript_preservesCjkCharacters() {
        val norm = PlaceMerger.normalizeName("東京タワー (Tokyo Tower)")
        assertEquals("東京タワー tokyo tower", norm)
    }

    @Test
    fun normalizeName_mixedScriptNames_preservesBothScripts() {
        val norm = PlaceMerger.normalizeName("Dubai Mall - دبي مول")
        assertEquals("dubai mall دبي مول", norm)
    }

    @Test
    fun normalizeName_punctuationVariations_removesPunctuationWithoutDeletingLetters() {
        val norm1 = PlaceMerger.normalizeName("St. George's Castle!")
        val norm2 = PlaceMerger.normalizeName("St Georges Castle")
        assertEquals(norm1, norm2)
        assertEquals("st georges castle", norm1)
    }

    @Test
    fun mergeCandidates_internationalNamesAtSameLocation_mergesCorrectly() {
        val c1 = candidate("1", "Ακρόπολη Αθηνών", 37.9715, 23.7257)
        val c2 = candidate("2", "Ακροπολη Αθηνων", 37.9716, 23.7258) // 10 meters away

        val merged = PlaceMerger.mergeCandidates(listOf(c1, c2))
        assertEquals("Greek accented and unaccented names at same location MUST merge", 1, merged.size)
        assertEquals(2, merged.first().sources.size)
    }

    @Test
    fun mergeCandidates_differentPlacesWithSimilarNames_remainsDistinct() {
        val c1 = candidate("1", "Café Central", 38.7112, -9.1423)
        val c2 = candidate("2", "Hotel Central", 38.7112, -9.1423) // Same location, different venue types

        val merged = PlaceMerger.mergeCandidates(listOf(c1, c2))
        assertEquals("Different venues at same location MUST remain distinct", 2, merged.size)
    }
}
