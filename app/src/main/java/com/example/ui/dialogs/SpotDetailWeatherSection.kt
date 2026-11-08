package com.example.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.WeatherReport
import com.example.data.repository.WeatherRepository
import com.example.ui.components.WeatherCard
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SpotDetailWeatherSection(spot: ScoutSpotEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var weatherReport by remember { mutableStateOf<WeatherReport?>(null) }
    var isWeatherLoading by remember { mutableStateOf(false) }

    val weatherRepository = remember {
        val db = AppDatabase.getDatabase(context)
        WeatherRepository(db.weatherDao())
    }

    fun fetchWeather(forceRefresh: Boolean = false) {
        scope.launch {
            isWeatherLoading = true
            weatherReport = weatherRepository.getOrFetchWeather(
                gemId = spot.id,
                latitude = spot.latitude,
                longitude = spot.longitude,
                category = spot.category,
                forceRefresh = forceRefresh
            )
            isWeatherLoading = false
        }
    }

    LaunchedEffect(spot.id) {
        fetchWeather(forceRefresh = false)
    }

    // Weather & Visit Suitability Forecast Card
    WeatherCard(
        weather = weatherReport,
        isLoading = isWeatherLoading,
        onRefreshClick = { fetchWeather(forceRefresh = true) }
    )
}
