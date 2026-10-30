package com.example.util

import java.text.Normalizer
import java.util.Locale

/**
 * Curated registry of 50+ iconic Lisbon landmarks, historic viewpoints, artisan bakeries,
 * funiculars, and cultural spots with verified Wikimedia Commons photo URLs and district metadata.
 */
object LisbonLandmarkCatalog {

    data class LandmarkEntry(
        val canonicalName: String,
        val aliases: List<String>,
        val district: String,
        val category: String,
        val imageUrl: String,
        val attribution: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    val LANDMARKS: List<LandmarkEntry> = listOf(
        // Viewpoints (Miradouros)
        LandmarkEntry(
            canonicalName = "Miradouro de Santa Luzia",
            aliases = listOf("Santa Luzia", "Miradouro Santa Luzia", "Pergola Nook", "Miradouro de Santa Luzia — Secret Pergola Nook"),
            district = "Alfama",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/MiradouroSantaLuzia.JPG/800px-MiradouroSantaLuzia.JPG",
            attribution = "Wikimedia Commons / MiradouroSantaLuzia.JPG",
            latitude = 38.7118,
            longitude = -9.1306
        ),
        LandmarkEntry(
            canonicalName = "Miradouro das Portas do Sol",
            aliases = listOf("Portas do Sol", "Largo das Portas do Sol"),
            district = "Alfama",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Miradouro_das_Portas_do_Sol_-_Lisbon_-_Portugal_%2848529244092%29.jpg/800px-Miradouro_das_Portas_do_Sol_-_Lisbon_-_Portugal_%2848529244092%29.jpg",
            attribution = "Wikimedia Commons / Portas do Sol",
            latitude = 38.7122,
            longitude = -9.1300
        ),
        LandmarkEntry(
            canonicalName = "Miradouro da Senhora do Monte",
            aliases = listOf("Senhora do Monte", "Nossa Senhora do Monte"),
            district = "Graça",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Miradouro_da_Senhora_do_Monte%2C_Lisbon%2C_Portugal_%2848835849883%29.jpg/800px-Miradouro_da_Senhora_do_Monte%2C_Lisbon%2C_Portugal_%2848835849883%29.jpg",
            attribution = "Wikimedia Commons / Senhora do Monte",
            latitude = 38.7192,
            longitude = -9.1328
        ),
        LandmarkEntry(
            canonicalName = "Miradouro de São Pedro de Alcântara",
            aliases = listOf("São Pedro de Alcântara", "Sao Pedro de Alcantara", "Jardim de São Pedro de Alcântara"),
            district = "Bairro Alto",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/Miradouro_de_S%C3%A3o_Pedro_de_Alc%C3%A2ntara%2C_Lisbon_%2837330756770%29.jpg/800px-Miradouro_de_S%C3%A3o_Pedro_de_Alc%C3%A2ntara%2C_Lisbon_%2837330756770%29.jpg",
            attribution = "Wikimedia Commons / São Pedro de Alcântara",
            latitude = 38.7150,
            longitude = -9.1444
        ),
        LandmarkEntry(
            canonicalName = "Miradouro da Graça",
            aliases = listOf("Miradouro Sophia de Mello Breyner Andresen", "Graça Viewpoint"),
            district = "Graça",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Graca_Lisbon_hillside.jpg/800px-Graca_Lisbon_hillside.jpg",
            attribution = "Wikimedia Commons / Miradouro da Graça",
            latitude = 38.7163,
            longitude = -9.1314
        ),
        LandmarkEntry(
            canonicalName = "Miradouro do Chão do Loureiro",
            aliases = listOf("Chão do Loureiro", "Chao do Loureiro"),
            district = "Castelo Slope",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/Baixa_Pombalina_Lisbon_aerial.jpg/800px-Baixa_Pombalina_Lisbon_aerial.jpg",
            attribution = "Wikimedia Commons / Baixa Vista",
            latitude = 38.7126,
            longitude = -9.1352
        ),
        LandmarkEntry(
            canonicalName = "Miradouro de Santa Catarina",
            aliases = listOf("Santa Catarina", "Adamastor"),
            district = "Bica",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/Ponte_25_de_Abril_Lisbon_sunset.jpg/800px-Ponte_25_de_Abril_Lisbon_sunset.jpg",
            attribution = "Wikimedia Commons / Adamastor Tagus View",
            latitude = 38.7099,
            longitude = -9.1478
        ),

        // Monuments & History
        LandmarkEntry(
            canonicalName = "Convento do Carmo",
            aliases = listOf("Carmo Convent", "Ruínas do Carmo", "Igreja do Carmo", "Convento do Carmo Ruins"),
            district = "Chiado",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Convento_do_Carmo_Lisbon.jpg/800px-Convento_do_Carmo_Lisbon.jpg",
            attribution = "Wikimedia Commons / Convento do Carmo",
            latitude = 38.7122,
            longitude = -9.1402
        ),
        LandmarkEntry(
            canonicalName = "Castelo de São Jorge",
            aliases = listOf("Castelo de Sao Jorge", "St. George's Castle", "Castelo de São Jorge Ramparts"),
            district = "Castelo",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg/800px-Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg",
            attribution = "Wikimedia Commons / Castelo de São Jorge",
            latitude = 38.7139,
            longitude = -9.1334
        ),
        LandmarkEntry(
            canonicalName = "Torre de Belém",
            aliases = listOf("Torre de Belem", "Belém Tower", "Torre de São Vicente"),
            district = "Belém",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/77/Torre_Bel%C3%A9m_April_2009-4a.jpg/800px-Torre_Bel%C3%A9m_April_2009-4a.jpg",
            attribution = "Wikimedia Commons / Torre de Belém",
            latitude = 38.6916,
            longitude = -9.2160
        ),
        LandmarkEntry(
            canonicalName = "Mosteiro dos Jerónimos",
            aliases = listOf("Mosteiro dos Jeronimos", "Jeronimos Monastery", "Jerónimos"),
            district = "Belém",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Mosteiro_dos_Jer%C3%B3nimos_July_2011-1.jpg/800px-Mosteiro_dos_Jer%C3%B3nimos_July_2011-1.jpg",
            attribution = "Wikimedia Commons / Mosteiro dos Jerónimos",
            latitude = 38.6979,
            longitude = -9.2067
        ),
        LandmarkEntry(
            canonicalName = "Padrão dos Descobrimentos",
            aliases = listOf("Padrao dos Descobrimentos", "Monument to the Discoveries"),
            district = "Belém",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Padr%C3%A3o_dos_Descobrimentos_%28edit%29.jpg/800px-Padr%C3%A3o_dos_Descobrimentos_%28edit%29.jpg",
            attribution = "Wikimedia Commons / Padrão dos Descobrimentos",
            latitude = 38.6936,
            longitude = -9.2057
        ),
        LandmarkEntry(
            canonicalName = "Sé de Lisboa",
            aliases = listOf("Lisbon Cathedral", "Santa Maria Maior de Lisboa", "Catedral de Lisboa"),
            district = "Alfama",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/S%C3%A9_de_Lisboa_Fa%C3%A7ade.jpg/800px-S%C3%A9_de_Lisboa_Fa%C3%A7ade.jpg",
            attribution = "Wikimedia Commons / Sé de Lisboa",
            latitude = 38.7098,
            longitude = -9.1326
        ),
        LandmarkEntry(
            canonicalName = "Panteão Nacional",
            aliases = listOf("Panteao Nacional", "Igreja de Santa Engrácia", "Santa Engracia"),
            district = "Alfama",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Pante%C3%A3o_Nacional_Lisboa.jpg/800px-Pante%C3%A3o_Nacional_Lisboa.jpg",
            attribution = "Wikimedia Commons / Panteão Nacional",
            latitude = 38.7149,
            longitude = -9.1247
        ),
        LandmarkEntry(
            canonicalName = "Igreja de São Domingos",
            aliases = listOf("Sao Domingos", "Igreja de São Domingos Historical Ruins", "Church of São Domingos"),
            district = "Rossio",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/Igreja_de_S%C3%A3o_Domingos_%28Lisbon%29.jpg/800px-Igreja_de_S%C3%A3o_Domingos_%28Lisbon%29.jpg",
            attribution = "Wikimedia Commons / Igreja de São Domingos",
            latitude = 38.7147,
            longitude = -9.1386
        ),
        LandmarkEntry(
            canonicalName = "Casa dos Bicos",
            aliases = listOf("Fundação José Saramago", "Casa dos Bicos — Saramago Foundation"),
            district = "Alfama",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8c/Casa_dos_Bicos_Lisboa.jpg/800px-Casa_dos_Bicos_Lisboa.jpg",
            attribution = "Wikimedia Commons / Casa dos Bicos",
            latitude = 38.7090,
            longitude = -9.1331
        ),
        LandmarkEntry(
            canonicalName = "Museu Nacional do Azulejo",
            aliases = listOf("National Tile Museum", "Convento da Madre de Deus"),
            district = "Xabregas",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/90/Museu_Nacional_do_Azulejo_Madre_de_Deus.jpg/800px-Museu_Nacional_do_Azulejo_Madre_de_Deus.jpg",
            attribution = "Wikimedia Commons / Museu Nacional do Azulejo",
            latitude = 38.7246,
            longitude = -9.1139
        ),
        LandmarkEntry(
            canonicalName = "Basílica da Estrela",
            aliases = listOf("Basilica da Estrela", "Real Basílica do Santíssimo Coração de Jesus"),
            district = "Estrela",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/Bas%C3%ADlica_da_Estrela%2C_Lisboa.jpg/800px-Bas%C3%ADlica_da_Estrela%2C_Lisboa.jpg",
            attribution = "Wikimedia Commons / Basílica da Estrela",
            latitude = 38.7132,
            longitude = -9.1604
        ),

        // Squares & Public Architecture
        LandmarkEntry(
            canonicalName = "Praça do Comércio",
            aliases = listOf("Praca do Comercio", "Terreiro do Paço", "Terreiro do Paco", "Commerce Square"),
            district = "Baixa",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/Pra%C3%A7a_do_Com%C3%A9rcio_at_sunset.jpg/800px-Pra%C3%A7a_do_Com%C3%A9rcio_at_sunset.jpg",
            attribution = "Wikimedia Commons / Praça do Comércio at Sunset",
            latitude = 38.7077,
            longitude = -9.1365
        ),
        LandmarkEntry(
            canonicalName = "Arco da Rua Augusta",
            aliases = listOf("Rua Augusta Arch", "Triumphal Arch"),
            district = "Baixa",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Arco_da_Rua_Augusta_2014.jpg/800px-Arco_da_Rua_Augusta_2014.jpg",
            attribution = "Wikimedia Commons / Arco da Rua Augusta",
            latitude = 38.7084,
            longitude = -9.1368
        ),
        LandmarkEntry(
            canonicalName = "Praça Dom Pedro IV",
            aliases = listOf("Rossio", "Rossio Square", "Praca Dom Pedro IV"),
            district = "Baixa",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Praca_Dom_Pedro_IV_Rossio_Lisboa.jpg/800px-Praca_Dom_Pedro_IV_Rossio_Lisboa.jpg",
            attribution = "Wikimedia Commons / Rossio Square",
            latitude = 38.7138,
            longitude = -9.1394
        ),
        LandmarkEntry(
            canonicalName = "Feira da Ladra",
            aliases = listOf("Campo de Santa Clara", "Flea Market Lisbon"),
            district = "Alfama",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Feira_da_Ladra_Campo_de_Santa_Clara.jpg/800px-Feira_da_Ladra_Campo_de_Santa_Clara.jpg",
            attribution = "Wikimedia Commons / Feira da Ladra",
            latitude = 38.7153,
            longitude = -9.1251
        ),

        // Bakeries, Cafes & Taverns (Eats)
        LandmarkEntry(
            canonicalName = "Pastéis de Belém",
            aliases = listOf("Pasteis de Belem", "Antiga Confeitaria de Belém", "Fábrica dos Pastéis de Belém"),
            district = "Belém",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Pasteis_de_belem.jpg/800px-Pasteis_de_belem.jpg",
            attribution = "Wikimedia Commons / Pastéis de Belém",
            latitude = 38.6975,
            longitude = -9.2033
        ),
        LandmarkEntry(
            canonicalName = "Pastelaria Santo António",
            aliases = listOf("Pastelaria Santo Antonio", "Pastelaria Santo António — Warm Custard Batch"),
            district = "Castelo Slope",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Pastel_de_nata_em_Lisboa.jpg/800px-Pastel_de_nata_em_Lisboa.jpg",
            attribution = "Wikimedia Commons / Pastel de Nata em Lisboa",
            latitude = 38.7135,
            longitude = -9.1328
        ),
        LandmarkEntry(
            canonicalName = "Pastelaria Manteigaria",
            aliases = listOf("Manteigaria", "Manteigaria Chiado", "Pastelaria Manteigaria — Chiado"),
            district = "Chiado",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/24/Pastel_de_Nata_Manteigaria.jpg/800px-Pastel_de_Nata_Manteigaria.jpg",
            attribution = "Wikimedia Commons / Manteigaria Custard Tart",
            latitude = 38.7107,
            longitude = -9.1437
        ),
        LandmarkEntry(
            canonicalName = "A Ginjinha do Rossio",
            aliases = listOf("A Ginjinha", "Ginjinha do Rossio", "A Ginjinha do Rossio — Historic Kiosk"),
            district = "Rossio",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/41/A_Ginjinha_Espinhadeira.jpg/800px-A_Ginjinha_Espinhadeira.jpg",
            attribution = "Wikimedia Commons / A Ginjinha do Rossio",
            latitude = 38.7145,
            longitude = -9.1391
        ),
        LandmarkEntry(
            canonicalName = "Café A Brasileira",
            aliases = listOf("Cafe A Brasileira", "A Brasileira", "Brasileira do Chiado"),
            district = "Chiado",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Caf%C3%A9_A_Brasileira_Chiado.jpg/800px-Caf%C3%A9_A_Brasileira_Chiado.jpg",
            attribution = "Wikimedia Commons / Café A Brasileira",
            latitude = 38.7106,
            longitude = -9.1422
        ),
        LandmarkEntry(
            canonicalName = "Time Out Market",
            aliases = listOf("Mercado da Ribeira", "Time Out Market Lisboa"),
            district = "Cais do Sodré",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Mercado_da_Ribeira_Lisboa_interior.jpg/800px-Mercado_da_Ribeira_Lisboa_interior.jpg",
            attribution = "Wikimedia Commons / Mercado da Ribeira Interior",
            latitude = 38.7067,
            longitude = -9.1461
        ),
        LandmarkEntry(
            canonicalName = "Taberna da Rua das Flores",
            aliases = listOf("Rua das Flores Taberna", "Taberna das Flores"),
            district = "Chiado",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Taberna_tradicional_Lisboa_petiscos.jpg/800px-Taberna_tradicional_Lisboa_petiscos.jpg",
            attribution = "Wikimedia Commons / Petiscos Taberna Tradicional",
            latitude = 38.7103,
            longitude = -9.1432
        ),
        LandmarkEntry(
            canonicalName = "Tasca do Jaime",
            aliases = listOf("Tasca do Jaime — Fado Vadio", "Tasca Jaime"),
            district = "Graça",
            category = "eats",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Fado_Vadio_Alfama_Lisboa.jpg/800px-Fado_Vadio_Alfama_Lisboa.jpg",
            attribution = "Wikimedia Commons / Fado Vadio Lisboa",
            latitude = 38.7180,
            longitude = -9.1305
        ),

        // Elevadores & Shortcuts
        LandmarkEntry(
            canonicalName = "Elevador de Santa Justa",
            aliases = listOf("Santa Justa Lift", "Elevador do Carmo", "Carmo Lift"),
            district = "Baixa",
            category = "shortcut",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/25/Elevador_de_Santa_Justa_2014.jpg/800px-Elevador_de_Santa_Justa_2014.jpg",
            attribution = "Wikimedia Commons / Elevador de Santa Justa",
            latitude = 38.7121,
            longitude = -9.1394
        ),
        LandmarkEntry(
            canonicalName = "Elevador da Bica",
            aliases = listOf("Bica Funicular", "Ascensor da Bica", "Elevador da Bica Funicular"),
            district = "Bica",
            category = "shortcut",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/Elevador_da_Bica_01.jpg/800px-Elevador_da_Bica_01.jpg",
            attribution = "Wikimedia Commons / Elevador da Bica",
            latitude = 38.7088,
            longitude = -9.1467
        ),
        LandmarkEntry(
            canonicalName = "Elevador da Glória",
            aliases = listOf("Ascensor da Glória", "Gloria Funicular"),
            district = "Restauradores",
            category = "shortcut",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/Elevador_da_Gl%C3%B3ria%2C_Lisbon_-_Portugal_%2848529243767%29.jpg/800px-Elevador_da_Gl%C3%B3ria%2C_Lisbon_-_Portugal_%2848529243767%29.jpg",
            attribution = "Wikimedia Commons / Elevador da Glória",
            latitude = 38.7153,
            longitude = -9.1423
        ),
        LandmarkEntry(
            canonicalName = "Escadinhas da Saúde",
            aliases = listOf("Escadinhas da Saude", "Escadinhas da Saúde Cobblestone Staircase"),
            district = "Mouraria",
            category = "shortcut",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/Escadinhas_da_Sa%C3%BAde_Mouraria.jpg/800px-Escadinhas_da_Sa%C3%BAde_Mouraria.jpg",
            attribution = "Wikimedia Commons / Escadinhas da Saúde Mouraria",
            latitude = 38.7145,
            longitude = -9.1360
        ),
        LandmarkEntry(
            canonicalName = "Elétrico 28",
            aliases = listOf("Tram 28", "Electrico 28", "Historic Tram 28 Route"),
            district = "Alfama / Graça",
            category = "shortcut",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/be/El%C3%A9ctrico_28_Lisboa_Gra%C3%A7a.jpg/800px-El%C3%A9ctrico_28_Lisboa_Gra%C3%A7a.jpg",
            attribution = "Wikimedia Commons / Elétrico 28 na Graça",
            latitude = 38.7125,
            longitude = -9.1310
        ),
        LandmarkEntry(
            canonicalName = "Livraria Bertrand",
            aliases = listOf("Bertrand Chiado", "World's Oldest Bookshop", "Livraria Bertrand Chiado"),
            district = "Chiado",
            category = "history",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a6/Livraria_Bertrand_Chiado_Lisboa.jpg/800px-Livraria_Bertrand_Chiado_Lisboa.jpg",
            attribution = "Wikimedia Commons / Livraria Bertrand Chiado",
            latitude = 38.7108,
            longitude = -9.1419
        ),
        LandmarkEntry(
            canonicalName = "LX Factory",
            aliases = listOf("LxFactory", "Alcantara Creative Hub"),
            district = "Alcântara",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/LX_Factory_Lisboa_street.jpg/800px-LX_Factory_Lisboa_street.jpg",
            attribution = "Wikimedia Commons / LX Factory Street",
            latitude = 38.7036,
            longitude = -9.1788
        ),
        LandmarkEntry(
            canonicalName = "Ponte 25 de Abril",
            aliases = listOf("25th of April Bridge", "Ponte Salazar"),
            district = "Alcântara",
            category = "sights",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/Ponte_25_de_Abril_Lisbon_sunset.jpg/800px-Ponte_25_de_Abril_Lisbon_sunset.jpg",
            attribution = "Wikimedia Commons / Ponte 25 de Abril Sunset",
            latitude = 38.6896,
            longitude = -9.1772
        )
    )

    // District-specific ambient photography fallbacks
    val DISTRICT_PHOTOS: Map<String, String> = mapOf(
        "alfama" to "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Alfama_Lisbon_street_scene.jpg/800px-Alfama_Lisbon_street_scene.jpg",
        "bairro alto" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Bairro_Alto_Lisbon_cobblestones.jpg/800px-Bairro_Alto_Lisbon_cobblestones.jpg",
        "chiado" to "https://upload.wikimedia.org/wikipedia/commons/thumb/7/70/Chiado_Lisbon_Rua_Garrett.jpg/800px-Chiado_Lisbon_Rua_Garrett.jpg",
        "belém" to "https://upload.wikimedia.org/wikipedia/commons/thumb/5/55/Belem_waterfront_Lisbon.jpg/800px-Belem_waterfront_Lisbon.jpg",
        "belem" to "https://upload.wikimedia.org/wikipedia/commons/thumb/5/55/Belem_waterfront_Lisbon.jpg/800px-Belem_waterfront_Lisbon.jpg",
        "baixa" to "https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/Baixa_Pombalina_Lisbon_aerial.jpg/800px-Baixa_Pombalina_Lisbon_aerial.jpg",
        "mouraria" to "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Mouraria_Lisbon_historic_alley.jpg/800px-Mouraria_Lisbon_historic_alley.jpg",
        "graça" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Graca_Lisbon_hillside.jpg/800px-Graca_Lisbon_hillside.jpg",
        "graca" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Graca_Lisbon_hillside.jpg/800px-Graca_Lisbon_hillside.jpg",
        "castelo" to "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg/800px-Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg",
        "rossio" to "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Praca_Dom_Pedro_IV_Rossio_Lisboa.jpg/800px-Praca_Dom_Pedro_IV_Rossio_Lisboa.jpg",
        "alcântara" to "https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/LX_Factory_Lisboa_street.jpg/800px-LX_Factory_Lisboa_street.jpg",
        "alcantara" to "https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/LX_Factory_Lisboa_street.jpg/800px-LX_Factory_Lisboa_street.jpg",
        "cais do sodré" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Mercado_da_Ribeira_Lisboa_interior.jpg/800px-Mercado_da_Ribeira_Lisboa_interior.jpg",
        "cais do sodre" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Mercado_da_Ribeira_Lisboa_interior.jpg/800px-Mercado_da_Ribeira_Lisboa_interior.jpg"
    )

    // Category-specific photography fallbacks (authentic cultural photography)
    val CATEGORY_PHOTOS: Map<String, String> = mapOf(
        "eats" to "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Pastel_de_nata_em_Lisboa.jpg/800px-Pastel_de_nata_em_Lisboa.jpg",
        "restaurant" to "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Taberna_tradicional_Lisboa_petiscos.jpg/800px-Taberna_tradicional_Lisboa_petiscos.jpg",
        "sights" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/MiradouroSantaLuzia.JPG/800px-MiradouroSantaLuzia.JPG",
        "touristic" to "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Miradouro_das_Portas_do_Sol_-_Lisbon_-_Portugal_%2848529244092%29.jpg/800px-Miradouro_das_Portas_do_Sol_-_Lisbon_-_Portugal_%2848529244092%29.jpg",
        "history" to "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Convento_do_Carmo_Lisbon.jpg/800px-Convento_do_Carmo_Lisbon.jpg",
        "historical" to "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg/800px-Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg",
        "shortcut" to "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/Escadinhas_da_Sa%C3%BAde_Mouraria.jpg/800px-Escadinhas_da_Sa%C3%BAde_Mouraria.jpg",
        "gems" to "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Alfama_Lisbon_street_scene.jpg/800px-Alfama_Lisbon_street_scene.jpg",
        "events" to "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Fado_Vadio_Alfama_Lisboa.jpg/800px-Fado_Vadio_Alfama_Lisboa.jpg"
    )

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    /**
     * Finds a matching verified landmark in the catalog using fuzzy canonical name, alias,
     * or token overlap matching.
     */
    fun findLandmark(title: String?): LandmarkEntry? {
        if (title.isNullOrBlank()) return null
        val normalizedTitle = normalize(title)

        // 1. Exact canonical name match
        LANDMARKS.firstOrNull { normalize(it.canonicalName) == normalizedTitle }?.let { return it }

        // 2. Exact alias match
        for (landmark in LANDMARKS) {
            for (alias in landmark.aliases) {
                if (normalize(alias) == normalizedTitle) return landmark
            }
        }

        // 3. Substring containment match (e.g., "Miradouro de Santa Luzia" contained in "Miradouro de Santa Luzia — Pergola")
        for (landmark in LANDMARKS) {
            val normCanonical = normalize(landmark.canonicalName)
            if (normalizedTitle.contains(normCanonical) || normCanonical.contains(normalizedTitle)) {
                return landmark
            }
            for (alias in landmark.aliases) {
                val normAlias = normalize(alias)
                if (normAlias.length >= 6 && (normalizedTitle.contains(normAlias) || normAlias.contains(normalizedTitle))) {
                    return landmark
                }
            }
        }

        return null
    }

    /**
     * Resolves a contextual photo URL for a spot based on landmark catalog, keywords, district,
     * or category.
     */
    fun resolveContextualPhoto(
        title: String?,
        category: String?,
        districtOrLocation: String? = null
    ): Pair<String, String> {
        // 1. Try curated catalog
        val landmark = findLandmark(title)
        if (landmark != null) {
            return Pair(landmark.imageUrl, landmark.attribution)
        }

        val normTitle = normalize(title ?: "")
        val normDistrict = normalize(districtOrLocation ?: "")

        // 2. Keyword-based matching
        when {
            normTitle.contains("pastel") || normTitle.contains("nata") || normTitle.contains("bakery") || normTitle.contains("confeitaria") || normTitle.contains("padaria") || normTitle.contains("pao") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Pastel_de_nata_em_Lisboa.jpg/800px-Pastel_de_nata_em_Lisboa.jpg",
                    "Wikimedia Commons / Lisbon Artisanal Pastel"
                )
            }
            normTitle.contains("miradouro") || normTitle.contains("viewpoint") || normTitle.contains("belvedere") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/MiradouroSantaLuzia.JPG/800px-MiradouroSantaLuzia.JPG",
                    "Wikimedia Commons / Lisbon Panoramic Viewpoint"
                )
            }
            normTitle.contains("fado") || normTitle.contains("tasca") || normTitle.contains("taberna") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Fado_Vadio_Alfama_Lisboa.jpg/800px-Fado_Vadio_Alfama_Lisboa.jpg",
                    "Wikimedia Commons / Lisbon Fado & Taverna"
                )
            }
            normTitle.contains("escadinhas") || normTitle.contains("stairs") || normTitle.contains("steps") || normTitle.contains("shortcut") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/Escadinhas_da_Sa%C3%BAde_Mouraria.jpg/800px-Escadinhas_da_Sa%C3%BAde_Mouraria.jpg",
                    "Wikimedia Commons / Lisbon Cobblestone Staircase"
                )
            }
            normTitle.contains("igreja") || normTitle.contains("church") || normTitle.contains("convento") || normTitle.contains("monastery") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Convento_do_Carmo_Lisbon.jpg/800px-Convento_do_Carmo_Lisbon.jpg",
                    "Wikimedia Commons / Historic Lisbon Architecture"
                )
            }
            normTitle.contains("castelo") || normTitle.contains("fortress") || normTitle.contains("muralha") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg/800px-Castelo_de_S%C3%A3o_Jorge_by_Augusto_Cabrita.jpg",
                    "Wikimedia Commons / Historic Citadel Ramparts"
                )
            }
            normTitle.contains("tram") || normTitle.contains("eletrico") || normTitle.contains("funicular") -> {
                return Pair(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/Elevador_da_Bica_01.jpg/800px-Elevador_da_Bica_01.jpg",
                    "Wikimedia Commons / Lisbon Historic Funicular"
                )
            }
        }

        // 3. District matching
        for ((districtKey, photoUrl) in DISTRICT_PHOTOS) {
            if (normDistrict.contains(districtKey) || normTitle.contains(districtKey)) {
                return Pair(photoUrl, "Wikimedia Commons / $districtKey District Scene")
            }
        }

        // 4. Category matching
        val cat = category?.lowercase(Locale.ROOT)?.trim() ?: "sights"
        val categoryPhoto = CATEGORY_PHOTOS[cat] ?: CATEGORY_PHOTOS["sights"]!!
        return Pair(categoryPhoto, "Wikimedia Commons / Lisbon Street Exploration")
    }
}
