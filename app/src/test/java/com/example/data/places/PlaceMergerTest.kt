package com.example.data.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMergerTest {

    @Test
    fun mergeCandidates_exactDuplicatesFromDifferentSources_mergedIntoOnePlaceWithAllAttributions() {
        val candidate1 = PlaceCandidate(
            sourceName = "OpenStreetMap Overpass API",
            sourceId = "osm_node_101",
            name = "Convento do Carmo",
            latitude = 38.7120,
            longitude = -9.1402,
            category = "history",
            description = "Gothic church ruins in Lisbon.",
            sourceUrl = "https://www.openstreetmap.org/node/101",
            license = "ODbL"
        )
        val candidate2 = PlaceCandidate(
            sourceName = "Wikidata Knowledge Graph",
            sourceId = "wikidata_Q83440",
            name = "Convento do Carmo",
            latitude = 38.7120,
            longitude = -9.1402,
            category = "history",
            description = "Archaeological museum and ruined Gothic convent in Lisbon.",
            imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Convento_do_Carmo.jpg",
            sourceUrl = "https://www.wikidata.org/wiki/Q83440",
            license = "CC0 1.0 Universal"
        )

        val mergedList = PlaceMerger.mergeCandidates(listOf(candidate1, candidate2))

        assertEquals("Exact duplicate candidates must merge into 1 place", 1, mergedList.size)
        val merged = mergedList.first()

        assertEquals("Convento do Carmo", merged.primaryName)
        assertEquals("Archaeological museum and ruined Gothic convent in Lisbon.", merged.primaryDescription)
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Convento_do_Carmo.jpg", merged.imageUrl)
        assertEquals(2, merged.sources.size)

        val sourceNames = merged.sources.map { it.sourceName }
        assertTrue(sourceNames.contains("OpenStreetMap Overpass API"))
        assertTrue(sourceNames.contains("Wikidata Knowledge Graph"))
    }

    @Test
    fun mergeCandidates_sameNameAtDifferentPlaces_shouldNotMerge() {
        val spotLisbon = PlaceCandidate(
            sourceName = "OpenStreetMap Overpass API",
            sourceId = "osm_node_201",
            name = "Pastelaria Central",
            latitude = 38.7112,
            longitude = -9.1423,
            category = "eats"
        )
        val spotSintra = PlaceCandidate(
            sourceName = "OpenStreetMap Overpass API",
            sourceId = "osm_node_202",
            name = "Pastelaria Central",
            latitude = 38.7980, // ~25km away in Sintra
            longitude = -9.3880,
            category = "eats"
        )

        val mergedList = PlaceMerger.mergeCandidates(listOf(spotLisbon, spotSintra))

        assertEquals("Same name at distant places (>50m) must NOT be merged", 2, mergedList.size)
    }

    @Test
    fun mergeCandidates_nearIdenticalNamesAtSamePlace_shouldMerge() {
        val candidate1 = PlaceCandidate(
            sourceName = "OpenStreetMap Overpass API",
            sourceId = "osm_node_301",
            name = "Miradouro de Santa Luzia",
            latitude = 38.7115,
            longitude = -9.1306,
            category = "sights"
        )
        val candidate2 = PlaceCandidate(
            sourceName = "Wikidata Knowledge Graph",
            sourceId = "wikidata_Q570116",
            name = "Miradouro Santa Luzia", // Near identical name (Jaro-Winkler similarity ~0.96)
            latitude = 38.7116, // 15 meters away
            longitude = -9.1305,
            category = "sights",
            description = "Famous panoramic viewpoint in Alfama overlooking the Tagus River."
        )

        val mergedList = PlaceMerger.mergeCandidates(listOf(candidate1, candidate2))

        assertEquals("Near identical names within 50m must be merged", 1, mergedList.size)
        val merged = mergedList.first()

        // Prefers Wikidata name & description
        assertEquals("Miradouro Santa Luzia", merged.primaryName)
        assertEquals("Famous panoramic viewpoint in Alfama overlooking the Tagus River.", merged.primaryDescription)
        assertEquals(2, merged.sources.size)
    }

    @Test
    fun mergeCandidates_listWithNoDuplicates_returnsAllCandidatesIntact() {
        val candidate1 = PlaceCandidate(
            sourceName = "OpenStreetMap",
            sourceId = "osm_1",
            name = "Castelo de São Jorge",
            latitude = 38.7139,
            longitude = -9.1334
        )
        val candidate2 = PlaceCandidate(
            sourceName = "OpenStreetMap",
            sourceId = "osm_2",
            name = "Torre de Belém",
            latitude = 38.6916,
            longitude = -9.2160
        )
        val candidate3 = PlaceCandidate(
            sourceName = "OpenStreetMap",
            sourceId = "osm_3",
            name = "Padrão dos Descobrimentos",
            latitude = 38.6936,
            longitude = -9.2057
        )

        val mergedList = PlaceMerger.mergeCandidates(listOf(candidate1, candidate2, candidate3))

        assertEquals("List without duplicates must return all candidates intact", 3, mergedList.size)
    }

    @Test
    fun jaroWinklerSimilarity_evaluatesAccuratelyForVenueNames() {
        val sim1 = PlaceMerger.calculateJaroWinklerSimilarity("miradouro de santa luzia", "miradouro santa luzia")
        assertTrue("High similarity expected for minor article variation", sim1 >= 0.85)

        val sim2 = PlaceMerger.calculateJaroWinklerSimilarity("castelo de sao jorge", "torre de belem")
        assertTrue("Similarity between distinct venue names should fall below 0.85 threshold", sim2 < 0.85)
    }
}
