package com.example.engine

import androidx.annotation.Keep
import com.example.engine.OfflineRouteEngine.haversineMeters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Keep
data class ElevationPoint(
    val distanceMeters: Double,
    val elevationMeters: Double,
    val slopeGradePct: Double
) {
    val formattedDistance: String
        get() = if (distanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.roundToInt()} m"
        }

    val formattedElevation: String
        get() = "${elevationMeters.roundToInt()} m"
}

@Keep
data class RouteElevationProfile(
    val points: List<ElevationPoint>,
    val totalAscentMeters: Double,
    val totalDescentMeters: Double,
    val minElevationMeters: Double,
    val maxElevationMeters: Double,
    val intensityLabel: String,
    val avgGradePct: Double
) {
    val formattedAscent: String
        get() = "+${totalAscentMeters.roundToInt()} m"

    val formattedDescent: String
        get() = "-${totalDescentMeters.roundToInt()} m"

    val formattedMinMax: String
        get() = "${minElevationMeters.roundToInt()}m - ${maxElevationMeters.roundToInt()}m"
}

/**
 * Lisbon Topography & Elevation Engine.
 * 100% offline interpolation of Lisbon's iconic "Seven Hills" (Sete Colinas) topography.
 * Accurately models the elevation contrast from Tagus riverbank (2m) up to Castelo & Bairro Alto (104m).
 */
object LisbonElevationModel {

    private data class TopoAnchor(
        val lat: Double,
        val lng: Double,
        val elevationMeters: Double,
        val name: String
    )

    // Accurate topographic survey anchors across central Lisbon
    private val topoAnchors = listOf(
        // Riverfront & Baixa (Valley)
        TopoAnchor(38.7063, -9.1368, 2.0, "Cais das Colunas"),
        TopoAnchor(38.7061, -9.1415, 3.0, "Ribeira das Naus"),
        TopoAnchor(38.7060, -9.1448, 4.0, "Cais do Sodré"),
        TopoAnchor(38.7075, -9.1364, 4.0, "Praça do Comércio"),
        TopoAnchor(38.7084, -9.1367, 6.0, "Arco da Rua Augusta"),
        TopoAnchor(38.7088, -9.1325, 6.0, "Campo das Cebolas"),
        TopoAnchor(38.7093, -9.1374, 9.0, "Rua Augusta / Conceição"),
        TopoAnchor(38.7104, -9.1381, 12.0, "Rua Augusta / Vitória"),
        TopoAnchor(38.7118, -9.1389, 14.0, "Rua Augusta / Santa Justa"),
        TopoAnchor(38.7130, -9.1394, 15.0, "Rossio South"),
        TopoAnchor(38.7138, -9.1393, 16.0, "Rossio Square"),
        TopoAnchor(38.7132, -9.1373, 14.0, "Praça da Figueira"),
        TopoAnchor(38.7147, -9.1396, 17.0, "Rossio Station"),
        TopoAnchor(38.7158, -9.1412, 19.0, "Praça dos Restauradores"),

        // Chiado & Carmo Hill
        TopoAnchor(38.7121, -9.1395, 24.0, "Santa Justa Lower"),
        TopoAnchor(38.7115, -9.1398, 28.0, "Rua do Carmo"),
        TopoAnchor(38.7107, -9.1402, 38.0, "Armazéns do Chiado"),
        TopoAnchor(38.7107, -9.1419, 44.0, "Rua Garrett"),
        TopoAnchor(38.7104, -9.1436, 48.0, "Praça Luís de Camões"),
        TopoAnchor(38.7114, -9.1410, 45.0, "Calçada do Sacramento"),
        TopoAnchor(38.7122, -9.1404, 52.0, "Largo do Carmo"),
        TopoAnchor(38.7132, -9.1413, 35.0, "Calçada do Duque"),

        // Bairro Alto & Santa Catarina
        TopoAnchor(38.7112, -9.1447, 56.0, "Rua da Rosa South"),
        TopoAnchor(38.7126, -9.1451, 65.0, "Rua da Rosa Center"),
        TopoAnchor(38.7142, -9.1453, 72.0, "Rua da Rosa North"),
        TopoAnchor(38.7108, -9.1465, 62.0, "Calçada do Combro"),
        TopoAnchor(38.7096, -9.1477, 68.0, "Miradouro de Santa Catarina"),
        TopoAnchor(38.7150, -9.1443, 85.0, "Miradouro de São Pedro de Alcântara"),
        TopoAnchor(38.7152, -9.1432, 88.0, "Elevador da Glória Upper"),

        // Alfama & Castelo Hill
        TopoAnchor(38.7098, -9.1328, 34.0, "Sé de Lisboa"),
        TopoAnchor(38.7107, -9.1319, 48.0, "Cruzes da Sé"),
        TopoAnchor(38.7116, -9.1306, 72.0, "Miradouro de Santa Luzia"),
        TopoAnchor(38.7121, -9.1300, 76.0, "Largo das Portas do Sol"),
        TopoAnchor(38.7125, -9.1290, 45.0, "Escadinhas de São Miguel"),
        TopoAnchor(38.7128, -9.1278, 22.0, "Rua dos Remédios"),
        TopoAnchor(38.7139, -9.1334, 104.0, "Castelo de São Jorge")
    )

    /**
     * Interpolates elevation in meters for any GPS coordinate using inverse distance weighting.
     */
    fun getElevationForCoordinate(lat: Double, lng: Double): Double {
        // Find 4 nearest topo anchors
        val nearest = topoAnchors.map { anchor ->
            val dist = haversineMeters(lat, lng, anchor.lat, anchor.lng)
            Pair(anchor, max(dist, 1.0))
        }.sortedBy { it.second }.take(4)

        if (nearest.first().second < 15.0) {
            return nearest.first().first.elevationMeters
        }

        var weightSum = 0.0
        var elevationSum = 0.0

        for ((anchor, dist) in nearest) {
            val weight = 1.0 / dist.pow(2.0)
            weightSum += weight
            elevationSum += anchor.elevationMeters * weight
        }

        return if (weightSum > 0) elevationSum / weightSum else 20.0
    }

    /**
     * Constructs a continuous elevation profile from path points.
     */
    fun buildElevationProfile(pathPoints: List<Pair<Double, Double>>): RouteElevationProfile {
        if (pathPoints.isEmpty()) {
            return RouteElevationProfile(
                points = emptyList(),
                totalAscentMeters = 0.0,
                totalDescentMeters = 0.0,
                minElevationMeters = 0.0,
                maxElevationMeters = 0.0,
                intensityLabel = "Gentle",
                avgGradePct = 0.0
            )
        }

        if (pathPoints.size == 1) {
            val elev = getElevationForCoordinate(pathPoints[0].first, pathPoints[0].second)
            val pt = ElevationPoint(0.0, elev, 0.0)
            return RouteElevationProfile(
                points = listOf(pt),
                totalAscentMeters = 0.0,
                totalDescentMeters = 0.0,
                minElevationMeters = elev,
                maxElevationMeters = elev,
                intensityLabel = "Gentle",
                avgGradePct = 0.0
            )
        }

        val rawPoints = mutableListOf<ElevationPoint>()
        var cumulativeDist = 0.0
        var prevLat = pathPoints[0].first
        var prevLng = pathPoints[0].second
        var prevElev = getElevationForCoordinate(prevLat, prevLng)

        rawPoints.add(ElevationPoint(0.0, prevElev, 0.0))

        for (i in 1 until pathPoints.size) {
            val lat = pathPoints[i].first
            val lng = pathPoints[i].second
            val segDist = haversineMeters(prevLat, prevLng, lat, lng)
            cumulativeDist += segDist
            val elev = getElevationForCoordinate(lat, lng)

            val dElev = elev - prevElev
            val gradePct = if (segDist > 0) (dElev / segDist) * 100.0 else 0.0

            rawPoints.add(ElevationPoint(cumulativeDist, elev, gradePct))
            prevLat = lat
            prevLng = lng
            prevElev = elev
        }

        // Apply 3-point moving average smoothing to eliminate artificial GPS micro-steps
        val smoothedPoints = mutableListOf<ElevationPoint>()
        for (i in rawPoints.indices) {
            val startIdx = max(0, i - 1)
            val endIdx = min(rawPoints.size - 1, i + 1)
            val avgElev = (startIdx..endIdx).map { rawPoints[it].elevationMeters }.average()
            val orig = rawPoints[i]
            smoothedPoints.add(orig.copy(elevationMeters = avgElev))
        }

        var totalAscent = 0.0
        var totalDescent = 0.0
        var minElev = Double.MAX_VALUE
        var maxElev = Double.MIN_VALUE

        for (i in smoothedPoints.indices) {
            val elev = smoothedPoints[i].elevationMeters
            if (elev < minElev) minElev = elev
            if (elev > maxElev) maxElev = elev

            if (i > 0) {
                val diff = elev - smoothedPoints[i - 1].elevationMeters
                if (diff > 0.3) {
                    totalAscent += diff
                } else if (diff < -0.3) {
                    totalDescent += -diff
                }
            }
        }

        val avgGrade = if (cumulativeDist > 0) (totalAscent / cumulativeDist) * 100.0 else 0.0

        val intensityLabel = when {
            totalAscent >= 45.0 || avgGrade >= 7.5 -> "Challenging Calçada Climb"
            totalAscent >= 20.0 || avgGrade >= 4.0 -> "Moderate Incline"
            else -> "Gentle Walk"
        }

        return RouteElevationProfile(
            points = smoothedPoints,
            totalAscentMeters = totalAscent,
            totalDescentMeters = totalDescent,
            minElevationMeters = minElev,
            maxElevationMeters = maxElev,
            intensityLabel = intensityLabel,
            avgGradePct = avgGrade
        )
    }
}
