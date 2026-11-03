package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceAttributionRowTest {

    @Test
    fun formatPlaceAttribution_osmData_formatsOpenStreetMapContributorsWithCopyrightUrl() {
        val attr1 = formatPlaceAttribution("OpenStreetMap Overpass API", "ODbL", "https://www.openstreetmap.org/node/101")
        assertEquals("© OpenStreetMap contributors", attr1.displayText)
        assertEquals("https://www.openstreetmap.org/node/101", attr1.sourceUrl)

        val attr2 = formatPlaceAttribution("osm_community", null, null)
        assertEquals("© OpenStreetMap contributors", attr2.displayText)
        assertEquals("https://www.openstreetmap.org/copyright", attr2.sourceUrl)
    }

    @Test
    fun formatPlaceAttribution_wikidata_formatsWikidataCc0WithWikidataUrl() {
        val attr = formatPlaceAttribution("Wikidata Knowledge Graph", "CC0 1.0 Universal", "https://www.wikidata.org/wiki/Q83440")
        assertEquals("Wikidata (CC0)", attr.displayText)
        assertEquals("https://www.wikidata.org/wiki/Q83440", attr.sourceUrl)
    }

    @Test
    fun formatPlaceAttribution_wikipedia_formatsWikipediaCcBySaWithWikipediaUrl() {
        val attr = formatPlaceAttribution("Wikipedia Summary", "CC BY-SA 4.0", "https://en.wikipedia.org/wiki/Convento_do_Carmo")
        assertEquals("Wikipedia (CC BY-SA)", attr.displayText)
        assertEquals("https://en.wikipedia.org/wiki/Convento_do_Carmo", attr.sourceUrl)
    }
}
