package com.example.engine

import androidx.annotation.Keep
import com.example.engine.OfflineRouteEngine.haversineMeters

/**
 * Representation of a junction, intersection, or landmark node in the offline walking network.
 */
@Keep
data class WalkNode(
    val id: String,
    val lat: Double,
    val lng: Double,
    val name: String
)

/**
 * Bidirectional or directional walking path segment in the offline vector network.
 */
@Keep
data class WalkEdge(
    val fromId: String,
    val toId: String,
    val distanceMeters: Double,
    val streetName: String,
    val isPedestrianOnly: Boolean = false,
    val isStairsOrCalçada: Boolean = false
)

/**
 * Comprehensive offline vector street and pedestrian walkway graph for Lisbon.
 * Connects major historical districts: Baixa, Chiado, Bairro Alto, Alfama, Rossio, and Riverfront.
 * Pure Kotlin, zero network dependency, instant graph queries.
 */
object VectorWalkGraph {

    private val baseNodes = mutableMapOf<String, WalkNode>()
    private val baseEdges = mutableListOf<WalkEdge>()

    init {
        buildLisbonWalkingNetwork()
    }

    private fun addNode(id: String, lat: Double, lng: Double, name: String): WalkNode {
        val node = WalkNode(id, lat, lng, name)
        baseNodes[id] = node
        return node
    }

    private fun addBidirectionalEdge(
        fromId: String,
        toId: String,
        streetName: String,
        isPedestrianOnly: Boolean = false,
        isStairsOrCalçada: Boolean = false
    ) {
        val nodeA = baseNodes[fromId] ?: return
        val nodeB = baseNodes[toId] ?: return
        val dist = haversineMeters(nodeA.lat, nodeA.lng, nodeB.lat, nodeB.lng)

        baseEdges.add(WalkEdge(fromId, toId, dist, streetName, isPedestrianOnly, isStairsOrCalçada))
        baseEdges.add(WalkEdge(toId, fromId, dist, streetName, isPedestrianOnly, isStairsOrCalçada))
    }

    private fun buildLisbonWalkingNetwork() {
        // --- 1. BAIXA & RIVERFRONT CORRIDOR ---
        addNode("praca_comercio", 38.7075, -9.1364, "Praça do Comércio (Terreiro do Paço)")
        addNode("cais_colunas", 38.7063, -9.1368, "Cais das Colunas (Riverbank)")
        addNode("arco_augusta", 38.7084, -9.1367, "Arco da Rua Augusta")
        addNode("augusta_conceicao", 38.7093, -9.1374, "Rua Augusta / Rua da Conceição")
        addNode("augusta_vitoria", 38.7104, -9.1381, "Rua Augusta / Rua da Vitória")
        addNode("augusta_santa_justa", 38.7118, -9.1389, "Rua Augusta / Rua de Santa Justa")
        addNode("rossio_south", 38.7130, -9.1394, "Rossio (Praça Dom Pedro IV - South)")
        addNode("rossio_center", 38.7138, -9.1393, "Rossio Fountain & Monument")
        addNode("rossio_north", 38.7147, -9.1396, "Rossio Station / D. Maria II")

        // Baixa parallel streets
        addNode("prata_conceicao", 38.7097, -9.1363, "Rua da Prata / Rua da Conceição")
        addNode("prata_santa_justa", 38.7121, -9.1378, "Rua da Prata / Rua de Santa Justa")
        addNode("praca_figueira", 38.7132, -9.1373, "Praça da Figueira")

        // Riverfront walkway
        addNode("ribeira_naus", 38.7061, -9.1415, "Ribeira das Naus Promenade")
        addNode("cais_sodre", 38.7060, -9.1448, "Cais do Sodré Promenade")

        // --- 2. CHIADO & CARMO CORRIDOR ---
        addNode("santa_justa_lift", 38.7121, -9.1395, "Elevador de Santa Justa (Lower Entrance)")
        addNode("rua_carmo", 38.7115, -9.1398, "Rua do Carmo (Pedestrian Walkway)")
        addNode("garrett_carmo", 38.7107, -9.1402, "Armazéns do Chiado (Rua Garrett / Rua do Carmo)")
        addNode("garrett_bertrand", 38.7107, -9.1419, "Rua Garrett (Livraria Bertrand)")
        addNode("praca_camoes", 38.7104, -9.1436, "Praça Luís de Camões")
        addNode("largo_carmo", 38.7122, -9.1404, "Largo do Carmo (Carmo Convent Ruins)")
        addNode("calcada_sacramento", 38.7114, -9.1410, "Calçada do Sacramento")
        addNode("calcada_duque", 38.7132, -9.1413, "Calçada do Duque (Historic Steps)")

        // --- 3. BAIRRO ALTO & SANTA CATARINA ---
        addNode("rua_rosa_south", 38.7112, -9.1447, "Rua da Rosa / Travessa da Queimada")
        addNode("rua_rosa_center", 38.7126, -9.1451, "Rua da Rosa / Rua do Diário de Notícias")
        addNode("rua_rosa_north", 38.7142, -9.1453, "Rua da Rosa / Travessa dos Fiéis de Deus")
        addNode("miradouro_alcantara", 38.7150, -9.1443, "Miradouro de São Pedro de Alcântara")
        addNode("elevador_gloria", 38.7152, -9.1432, "Elevador da Glória (Upper Station)")
        addNode("restauradores", 38.7158, -9.1412, "Praça dos Restauradores")
        addNode("calcada_combro", 38.7108, -9.1465, "Calçada do Combro")
        addNode("miradouro_santa_catarina", 38.7096, -9.1477, "Miradouro de Santa Catarina (Adamastor)")

        // --- 4. ALFAMA & CASTELO CORRIDOR ---
        addNode("se_cathedral", 38.7098, -9.1328, "Sé de Lisboa (Cathedral)")
        addNode("cruzes_se", 38.7107, -9.1319, "Cruzes da Sé / Miradouro Steps")
        addNode("miradouro_santa_luzia", 38.7116, -9.1306, "Miradouro de Santa Luzia (Azulejos)")
        addNode("portas_do_sol", 38.7121, -9.1300, "Largo das Portas do Sol")
        addNode("castelo_entry", 38.7139, -9.1334, "Castelo de São Jorge (Castle Entrance)")
        addNode("escadinhas_sao_miguel", 38.7125, -9.1290, "Escadinhas de São Miguel (Alfama Alleys)")
        addNode("remedios_fado", 38.7128, -9.1278, "Rua dos Remédios (Alfama Fado Street)")
        addNode("campo_cebolas", 38.7088, -9.1325, "Campo das Cebolas / Casa dos Bicos")

        // --- CONNECT EDGES ---

        // Riverfront & Baixa Main Spine
        addBidirectionalEdge("cais_colunas", "praca_comercio", "Cais das Colunas Promenade", isPedestrianOnly = true)
        addBidirectionalEdge("praca_comercio", "arco_augusta", "Terreiro do Paço", isPedestrianOnly = true)
        addBidirectionalEdge("arco_augusta", "augusta_conceicao", "Rua Augusta", isPedestrianOnly = true)
        addBidirectionalEdge("augusta_conceicao", "augusta_vitoria", "Rua Augusta", isPedestrianOnly = true)
        addBidirectionalEdge("augusta_vitoria", "augusta_santa_justa", "Rua Augusta", isPedestrianOnly = true)
        addBidirectionalEdge("augusta_santa_justa", "rossio_south", "Rua Augusta", isPedestrianOnly = true)
        addBidirectionalEdge("rossio_south", "rossio_center", "Praça Dom Pedro IV", isPedestrianOnly = true)
        addBidirectionalEdge("rossio_center", "rossio_north", "Rossio Square", isPedestrianOnly = true)

        // Baixa East Crossings & Figueira
        addBidirectionalEdge("praca_comercio", "campo_cebolas", "Ribeira das Naus / Av. Infante Dom Henrique")
        addBidirectionalEdge("augusta_conceicao", "prata_conceicao", "Rua da Conceição (Tram 28 Line)")
        addBidirectionalEdge("augusta_santa_justa", "prata_santa_justa", "Rua de Santa Justa", isPedestrianOnly = true)
        addBidirectionalEdge("prata_santa_justa", "praca_figueira", "Rua dos Fanqueiros")
        addBidirectionalEdge("rossio_center", "praca_figueira", "Praça da Figueira Passage", isPedestrianOnly = true)

        // Riverfront West Corridor
        addBidirectionalEdge("cais_colunas", "ribeira_naus", "Ribeira das Naus Promenade", isPedestrianOnly = true)
        addBidirectionalEdge("ribeira_naus", "cais_sodre", "Avenida Ribeira das Naus", isPedestrianOnly = true)
        addBidirectionalEdge("cais_sodre", "miradouro_santa_catarina", "Calçada da Bica (Bica Funicular Path)", isStairsOrCalçada = true)

        // Baixa to Chiado Connections
        addBidirectionalEdge("augusta_santa_justa", "santa_justa_lift", "Rua de Santa Justa", isPedestrianOnly = true)
        addBidirectionalEdge("santa_justa_lift", "rua_carmo", "Rua do Carmo Junction", isPedestrianOnly = true)
        addBidirectionalEdge("rua_carmo", "rossio_south", "Rua do Carmo", isPedestrianOnly = true)
        addBidirectionalEdge("rua_carmo", "garrett_carmo", "Rua do Carmo", isPedestrianOnly = true)
        addBidirectionalEdge("augusta_vitoria", "garrett_carmo", "Calçada do Sacramento lower slope", isStairsOrCalçada = true)

        // Chiado Main Spine
        addBidirectionalEdge("garrett_carmo", "garrett_bertrand", "Rua Garrett", isPedestrianOnly = false)
        addBidirectionalEdge("garrett_bertrand", "praca_camoes", "Rua Garrett / Largo do Chiado", isPedestrianOnly = false)
        addBidirectionalEdge("garrett_bertrand", "calcada_sacramento", "Calçada do Sacramento", isStairsOrCalçada = true)
        addBidirectionalEdge("calcada_sacramento", "largo_carmo", "Calçada do Sacramento", isStairsOrCalçada = true)
        addBidirectionalEdge("largo_carmo", "santa_justa_lift", "Carmo Catwalk Terrace", isPedestrianOnly = true)
        addBidirectionalEdge("largo_carmo", "calcada_duque", "Calçada do Duque (Upper)", isStairsOrCalçada = true)
        addBidirectionalEdge("calcada_duque", "rossio_north", "Calçada do Duque Stone Stairs", isPedestrianOnly = true, isStairsOrCalçada = true)

        // Chiado to Bairro Alto
        addBidirectionalEdge("praca_camoes", "rua_rosa_south", "Rua das Flores / Praça Luís de Camões")
        addBidirectionalEdge("praca_camoes", "calcada_combro", "Rua do Loreto")
        addBidirectionalEdge("calcada_combro", "miradouro_santa_catarina", "Rua Marechal Saldanha", isPedestrianOnly = true)

        // Bairro Alto Grid
        addBidirectionalEdge("rua_rosa_south", "rua_rosa_center", "Rua da Rosa (Bairro Alto Cobblestones)", isStairsOrCalçada = true)
        addBidirectionalEdge("rua_rosa_center", "rua_rosa_north", "Rua da Rosa", isStairsOrCalçada = true)
        addBidirectionalEdge("rua_rosa_north", "miradouro_alcantara", "Rua de São Pedro de Alcântara", isPedestrianOnly = false)
        addBidirectionalEdge("miradouro_alcantara", "elevador_gloria", "Jardim de São Pedro de Alcântara", isPedestrianOnly = true)
        addBidirectionalEdge("elevador_gloria", "restauradores", "Calçada da Glória (Steep Slope)", isPedestrianOnly = true, isStairsOrCalçada = true)
        addBidirectionalEdge("rossio_north", "restauradores", "Praça dos Restauradores Avenue")

        // Baixa to Alfama Connections
        addBidirectionalEdge("campo_cebolas", "se_cathedral", "Rua das Pedras Negras", isStairsOrCalçada = true)
        addBidirectionalEdge("prata_conceicao", "se_cathedral", "Rua da Conceição / Rua de Santo António da Sé")
        addBidirectionalEdge("se_cathedral", "cruzes_se", "Largo da Sé / Cruzes da Sé", isStairsOrCalçada = true)
        addBidirectionalEdge("cruzes_se", "miradouro_santa_luzia", "Rua do Chão da Feira / Rua do Limoeiro")
        addBidirectionalEdge("miradouro_santa_luzia", "portas_do_sol", "Largo de Santa Luzia", isPedestrianOnly = true)
        addBidirectionalEdge("portas_do_sol", "castelo_entry", "Rua do Chão da Feira (Castle Path)", isStairsOrCalçada = true)
        addBidirectionalEdge("portas_do_sol", "escadinhas_sao_miguel", "Escadinhas de São Miguel (Alfama Stairs)", isPedestrianOnly = true, isStairsOrCalçada = true)
        addBidirectionalEdge("escadinhas_sao_miguel", "remedios_fado", "Beco do Carneiro / Rua dos Remédios", isPedestrianOnly = true, isStairsOrCalçada = true)
    }

    /**
     * Returns an immutable copy of the base nodes.
     */
    fun getNodes(): Map<String, WalkNode> = baseNodes.toMap()

    /**
     * Returns an immutable copy of the base edges.
     */
    fun getEdges(): List<WalkEdge> = baseEdges.toList()

    /**
     * Finds the closest node in the network to a given coordinate.
     */
    fun findClosestNode(lat: Double, lng: Double): WalkNode? {
        if (baseNodes.isEmpty()) return null
        return baseNodes.values.minByOrNull { node ->
            haversineMeters(lat, lng, node.lat, node.lng)
        }
    }
}
