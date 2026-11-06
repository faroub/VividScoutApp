package com.example.engine

/**
 * Resolves the offline topographic elevation model for a walking route's region.
 *
 * [LisbonElevationModel] applies exclusively to central Lisbon — the area actually
 * covered by its surveyed topographic anchors (Baixa, Chiado, Bairro Alto and the
 * Alfama/Castelo quarter). Resolution therefore requires BOTH:
 *
 *  - city identity: a supplied name must be Lisbon or one of the central districts
 *    covered by the anchors;
 *  - geographic coverage: the origin coordinate must lie inside the model's
 *    verified anchor envelope. This check is authoritative — city-name matching
 *    alone is never sufficient.
 *
 * Neighboring areas with no surveyed coverage — Sintra, Belém, Cascais — and every
 * unsupported city resolve to null, so callers keep their elevation charts hidden
 * rather than presenting another city's model as local terrain.
 */
object CityElevationResolver {

    // Lisbon and the central districts whose territory lies inside
    // LisbonElevationModel's anchor coverage. Deliberately excludes districts
    // outside that coverage (Belém, Ajuda) and separate cities (Sintra).
    private val COVERED_LISBON_NAMES = setOf(
        "lisbon", "lisboa",
        "alfama", "chiado", "baixa", "rossio", "bairro alto",
        "castelo", "mouraria", "sé",
        "santa catarina", "cais do sodré", "príncipe real"
    )

    fun resolveModelForCity(cityName: String?, originLat: Double = 0.0, originLng: Double = 0.0): ElevationModel? {
        if (!isValidCoordinate(originLat, originLng)) return null

        // Geographic coverage is authoritative: never serve the Lisbon model
        // outside its verified anchor envelope.
        if (!LisbonElevationModel.coversCoordinate(originLat, originLng)) return null

        // When a city name is supplied it must identify Lisbon or one of the
        // covered central districts. An unrelated name resolves to no model
        // even when the coordinates happen to be inside Lisbon.
        if (!cityName.isNullOrBlank()) {
            val nameLower = cityName.lowercase()
            if (COVERED_LISBON_NAMES.none { nameLower.contains(it) }) return null
        }

        return LisbonElevationModel
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean =
        !lat.isNaN() && !lng.isNaN() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)
}
