package com.example.engine

interface ElevationModel {
    val isSupported: Boolean
    fun getElevationForCoordinate(lat: Double, lng: Double): Double?
    fun buildElevationProfile(pathPoints: List<Pair<Double, Double>>): RouteElevationProfile?
}

object NullElevationModel : ElevationModel {
    override val isSupported: Boolean = false
    override fun getElevationForCoordinate(lat: Double, lng: Double): Double? = null
    override fun buildElevationProfile(pathPoints: List<Pair<Double, Double>>): RouteElevationProfile? = null
}
