package com.example.data.sources

/**
 * Metadata representation of a Touristic Place Data Source available in Vivid Scout.
 */
data class TouristicSourceInfo(
    val id: String,
    val name: String,
    val providerBadge: String,
    val categoryTag: String,
    val description: String,
    val coverageScope: String,
    val keyFeatures: List<String>,
    val apiEndpoint: String,
    val isActive: Boolean = true
)

/**
 * Comprehensive registry of open, crowd-sourced, municipal, and AI-driven touristic place data sources.
 */
object TouristicSourceRegistry {

    val sources: List<TouristicSourceInfo> = listOf(
        TouristicSourceInfo(
            id = "overpass_osm",
            name = "OpenStreetMap (OSM) / Overpass API",
            providerBadge = "OSM Overpass",
            categoryTag = "Offline Vector Maps & POIs",
            description = "The gold standard for offline-first vector maps. Provides raw spatial vector data for historical landmarks, footpaths, shortcuts, tile walls, and viewpoints worldwide.",
            coverageScope = "Worldwide (100M+ community-mapped POIs)",
            keyFeatures = listOf(
                "Precise lat/lng coordinates & vector tags",
                "Footpaths, shortcuts, and scenic miradouros",
                "Community heritage & wikimedia_commons tags"
            ),
            apiEndpoint = "https://overpass-api.de/api/interpreter"
        ),
        TouristicSourceInfo(
            id = "open_trip_map",
            name = "OpenTripMap API",
            providerBadge = "OpenTripMap",
            categoryTag = "Ranked Travel Directory",
            description = "Specialized travel provider built on OSM and Wikipedia. Offers categories specifically ranked by tourist popularity, covering nature features, cultural heritage, and architecture.",
            coverageScope = "Global (Attractions, Monuments, Parks)",
            keyFeatures = listOf(
                "Popularity & heritage ranking (Rate 1-7)",
                "Rich taxonomy (historic, natural, cultural, food)",
                "High-speed radius search"
            ),
            apiEndpoint = "https://api.opentripmap.com/0.1/en/places/radius"
        ),
        TouristicSourceInfo(
            id = "wikivoyage_wikidata",
            name = "Wikidata / Wikipedia API",
            providerBadge = "Wikidata Heritage",
            categoryTag = "Knowledge Graph & Media",
            description = "Structured, multi-language descriptions, historical context, and coordinate-linked imagery for cultural sites across the Wikidata knowledge graph.",
            coverageScope = "Worldwide (SPARQL Graph Database)",
            keyFeatures = listOf(
                "Multilingual monument descriptions",
                "Cross-referenced historical Wikidata IDs",
                "Coordinate-linked media & article summaries"
            ),
            apiEndpoint = "https://query.wikidata.org/sparql"
        ),
        TouristicSourceInfo(
            id = "geonames_gazetteer",
            name = "GeoNames Gazetteers",
            providerBadge = "GeoNames",
            categoryTag = "Global Geographic POIs",
            description = "A massive, free geographical database covering over 25 million geographical names, hilltops, historical points of interest, and gazetteer landmarks.",
            coverageScope = "Global (25M+ Geographical Names & POIs)",
            keyFeatures = listOf(
                "Toponym gazetteers & elevation hilltops",
                "Geographical feature classification",
                "Coordinates-linked Wikipedia articles"
            ),
            apiEndpoint = "https://secure.geonames.org/findNearbyWikipediaJSON"
        ),
        TouristicSourceInfo(
            id = "unesco_world_heritage",
            name = "UNESCO World Heritage API",
            providerBadge = "UNESCO Site",
            categoryTag = "Official World Heritage",
            description = "Provides spatial data, criteria classification, and official historical background for globally recognized cultural and natural heritage locations.",
            coverageScope = "Worldwide (1,100+ UNESCO World Heritage Properties)",
            keyFeatures = listOf(
                "Official UNESCO World Heritage site IDs",
                "Inscribed Cultural & Natural Criteria badges",
                "Historical significance & preservation background"
            ),
            apiEndpoint = "UNESCO Official Property Registry / SPARQL"
        ),
        TouristicSourceInfo(
            id = "europeana_cultural",
            name = "Europeana Cultural API",
            providerBadge = "Europeana",
            categoryTag = "Digital Cultural Archives",
            description = "A vast repository of digital cultural heritage items from thousands of European institutions, perfect for deep localized history and artwork.",
            coverageScope = "European Museums, Libraries & Archives (50M+ items)",
            keyFeatures = listOf(
                "Digital artwork thumbnails & archives",
                "Institutional provenance & data providers",
                "Localized European cultural history"
            ),
            apiEndpoint = "https://api.europeana.eu/record/v2/search.json"
        ),
        TouristicSourceInfo(
            id = "municipal_open_data",
            name = "Data.gov / Municipal Open Portals",
            providerBadge = "City Open Data",
            categoryTag = "Municipal Open Data",
            description = "Municipal 'Open Data' catalogs containing exact spatial coordinates for public art, historic trails, bike paths, and municipal public parks.",
            coverageScope = "Global City Municipal Open Data Catalogs",
            keyFeatures = listOf(
                "Exact municipal public art coordinates",
                "City-approved historic walking trails",
                "Public parks & municipal heritage markers"
            ),
            apiEndpoint = "City Municipal Open Data Portals"
        ),
        TouristicSourceInfo(
            id = "wikimedia_commons",
            name = "Wikimedia Commons API",
            providerBadge = "Wikimedia HD",
            categoryTag = "HD Photo Metadata",
            description = "Authentic, high-resolution (1200px HD) location photography directly resolved from Wikimedia media search & page image summaries.",
            coverageScope = "Worldwide (90M+ media files)",
            keyFeatures = listOf(
                "1200px High-Definition crisp thumbnails",
                "Direct OSM tag resolution (File: tag)",
                "Zero watermarks & public domain attribution"
            ),
            apiEndpoint = "https://commons.wikimedia.org/w/api.php"
        ),
        TouristicSourceInfo(
            id = "wikipedia_geosearch",
            name = "Wikipedia Geosearch Engine",
            providerBadge = "Wikipedia Geo",
            categoryTag = "Geolocated Travel Articles",
            description = "Geospatial REST API fetching Wikipedia travel articles within a radius of user coordinates, enriching spots with rich historical context.",
            coverageScope = "Global Wikipedia Pages (300+ languages)",
            keyFeatures = listOf(
                "Geocoded article summaries",
                "Rich historical context & origins",
                "Multi-language fallback (EN, PT, ES, FR, DE, JA)"
            ),
            apiEndpoint = "https://en.wikipedia.org/w/api.php?action=query&generator=geosearch"
        ),
        TouristicSourceInfo(
            id = "firebase_gemini_ai",
            name = "Firebase AI Logic (Gemini 3.8 Flash)",
            providerBadge = "Gemini AI",
            categoryTag = "Insiders & Hidden Gems",
            description = "Real-time AI spot generator identifying hidden alleyway cafes, secret panoramic miradouros, and obscure local culinary spots.",
            coverageScope = "Real-Time AI Multimodal Discovery",
            keyFeatures = listOf(
                "Generates non-touristy hidden gems",
                "Produces curated pro-tips & walk times",
                "Offline-first sync directly into Room"
            ),
            apiEndpoint = "Firebase AI Logic / Gemini SDK"
        )
    )
}
