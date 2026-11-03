package com.example.data.network

import com.example.data.local.ScoutSpotEntity
import com.example.data.network.model.SparqlBindingValue
import com.example.data.network.model.SparqlHead
import com.example.data.network.model.SparqlResults
import com.example.data.network.model.WikidataSparqlResponse
import com.example.data.network.model.WikipediaSummaryResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WikidataSearchServiceIntegrationTest {

    private class FakeWikidataSparqlApi : WikidataSparqlApi {
        var queryReceived: String? = null

        override suspend fun querySparql(sparqlQuery: String, format: String): WikidataSparqlResponse {
            queryReceived = sparqlQuery
            val bindings = listOf(
                mapOf(
                    "item" to SparqlBindingValue(value = "http://www.wikidata.org/entity/Q263"),
                    "itemLabel" to SparqlBindingValue(value = "Torre de Belém"),
                    "itemDescription" to SparqlBindingValue(value = "16th-century fortification in Lisbon"),
                    "coord" to SparqlBindingValue(value = "Point(-9.2160 38.6916)"),
                    "image" to SparqlBindingValue(value = "http://commons.wikimedia.org/wiki/Special:FilePath/Torre_Bel%C3%A9m_2016.jpg")
                ),
                mapOf(
                    "item" to SparqlBindingValue(value = "http://www.wikidata.org/entity/Q83440"),
                    "itemLabel" to SparqlBindingValue(value = "Pena Palace"),
                    "itemDescription" to SparqlBindingValue(value = "Romanticist castle in Sintra"),
                    "coord" to SparqlBindingValue(value = "Point(-9.3906 38.7876)"),
                    "image" to SparqlBindingValue(value = "http://commons.wikimedia.org/wiki/Special:FilePath/Pena_Palace.jpg")
                )
            )
            return WikidataSparqlResponse(
                head = SparqlHead(vars = listOf("item", "itemLabel", "itemDescription", "coord", "image")),
                results = SparqlResults(bindings = bindings)
            )
        }
    }

    private class FakeWikipediaApi : WikipediaApi {
        var requestedTitles = mutableListOf<String>()

        override suspend fun getPageSummary(title: String): WikipediaSummaryResponse {
            requestedTitles.add(title)
            return when {
                title.contains("Torre") || title.contains("Bel") -> {
                    WikipediaSummaryResponse(
                        title = "Torre de Belém",
                        extract = "The Belém Tower is a 16th-century fortification located in Lisbon that served as a point of embarkation and disembarkation for Portuguese explorers.",
                        description = "Historic tower in Lisbon"
                    )
                }
                title.contains("Pena") -> {
                    WikipediaSummaryResponse(
                        title = "Pena Palace",
                        extract = "Pena National Palace is a Romanticist castle in São Pedro de Penaferrim, in the municipality of Sintra, Portugal. The castle stands on the top of a hill in the Sintra Mountains.",
                        description = "Romanticist castle in Portugal"
                    )
                }
                else -> {
                    WikipediaSummaryResponse(
                        title = title,
                        extract = null,
                        description = null
                    )
                }
            }
        }
    }

    @Test
    fun fetchNearbyWikidataSpots_executesSparqlAndMapsEntitiesWithHdUrls(): Unit = runBlocking {
        val fakeSparqlApi = FakeWikidataSparqlApi()
        val fakeWikiApi = FakeWikipediaApi()
        val service = WikidataSearchService(sparqlApi = fakeSparqlApi, wikipediaApi = fakeWikiApi)

        val spots = service.fetchNearbyWikidataSpots(
            lat = 38.7112,
            lon = -9.1423,
            category = "history",
            radiusKm = 5.0,
            primaryLang = "en"
        )

        assertNotNull(fakeSparqlApi.queryReceived)
        assertTrue(fakeSparqlApi.queryReceived!!.contains("wikibase:around"))
        assertEquals(2, spots.size)

        val belemSpot = spots.find { it.id == "wikidata_Q263" }
        assertNotNull(belemSpot)
        assertEquals("Torre de Belém", belemSpot!!.title)
        assertEquals("history", belemSpot.category)
        assertEquals("Wikidata Heritage", belemSpot.statusBadge)
        assertTrue(belemSpot.imageUrl.contains("Special:FilePath/"))
        assertTrue(belemSpot.imageUrl.contains("width=1200"))
        assertTrue(belemSpot.tagsRaw.contains("wikidata|||Q263"))
        assertTrue(belemSpot.description.contains("16th-century fortification"))
    }

    @Test
    fun enrichSpotsWithWikipediaSummaries_injectsTextSummaryInSecondaryFlow(): Unit = runBlocking {
        val fakeSparqlApi = FakeWikidataSparqlApi()
        val fakeWikiApi = FakeWikipediaApi()
        val service = WikidataSearchService(sparqlApi = fakeSparqlApi, wikipediaApi = fakeWikiApi)

        val initialSpot = ScoutSpotEntity(
            id = "wikidata_Q263",
            title = "Torre de Belém",
            description = "Historical & cultural landmark cataloged on Wikidata Knowledge Graph (Q263).",
            category = "history",
            distance = "Nearby",
            walkMinutes = 7,
            imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Torre_Belem.jpg?width=1200",
            badgeLabel = "Wikidata",
            statusBadge = "Wikidata Heritage",
            tagsRaw = "wikidata|||Q263|||File:Torre_Belem.jpg|||history",
            proTip = "Wikidata QID: Q263",
            latitude = 38.6916,
            longitude = -9.2160
        )

        val enrichedSpots = service.enrichSpotsWithWikipediaSummaries(listOf(initialSpot), primaryLang = "en")

        assertEquals(1, enrichedSpots.size)
        val enriched = enrichedSpots[0]
        assertTrue(enriched.description.contains("point of embarkation and disembarkation for Portuguese explorers"))
        assertTrue(enriched.proTip?.contains("Q263") == true)
        assertTrue(fakeWikiApi.requestedTitles.isNotEmpty())
    }

    @Test
    fun enrichSpotsWithWikipediaSummaries_skipsAlreadyEnrichedSpotInRoom(): Unit = runBlocking {
        val fakeSparqlApi = FakeWikidataSparqlApi()
        val fakeWikiApi = FakeWikipediaApi()
        val service = WikidataSearchService(sparqlApi = fakeSparqlApi, wikipediaApi = fakeWikiApi)

        val alreadyEnrichedSpot = ScoutSpotEntity(
            id = "wikidata_Q263",
            title = "Torre de Belém",
            description = "The Belém Tower is a 16th-century fortification located in Lisbon that served as a point of embarkation and disembarkation for Portuguese explorers.",
            category = "history",
            distance = "Nearby",
            walkMinutes = 7,
            imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Torre_Belem.jpg?width=1200",
            badgeLabel = "Wikidata",
            statusBadge = "Wikidata Heritage",
            tagsRaw = "wikidata|||Q263|||File:Torre_Belem.jpg|||history",
            proTip = "Wikidata QID: Q263",
            latitude = 38.6916,
            longitude = -9.2160
        )

        val resultSpots = service.enrichSpotsWithWikipediaSummaries(listOf(alreadyEnrichedSpot), primaryLang = "en")

        assertEquals(1, resultSpots.size)
        assertEquals(alreadyEnrichedSpot.description, resultSpots[0].description)
        assertTrue(fakeWikiApi.requestedTitles.isEmpty())
    }
}
