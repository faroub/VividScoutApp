package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GlobalCity
import com.example.data.model.GlobalCityCatalog
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLowest
import kotlinx.coroutines.delay

@Composable
fun GlobalCitySelectionDialog(
    selectedCity: GlobalCity?,
    isUsingGps: Boolean,
    onSelectCity: (GlobalCity) -> Unit,
    onSelectGpsLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchingGeocode by remember { mutableStateOf(false) }
    var geocodedResults by remember { mutableStateOf<List<GlobalCity>>(emptyList()) }

    val popularCities = remember {
        GlobalCityCatalog.POPULAR_WORLD_CITIES.filter { it.id != "gps_current" }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 3) {
            delay(400) // Debounce geocoding network call
            isSearchingGeocode = true
            val results = GlobalCityCatalog.geocodeGlobalCity(searchQuery)
            geocodedResults = results
            isSearchingGeocode = false
        } else {
            geocodedResults = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, BrandTeal.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .testTag("global_city_dialog"),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(BrandTeal, Secondary)))
                                .padding(1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFF0D132D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Global Exploration",
                                    tint = BrandTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Global Destination",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Explore spots anywhere on Earth",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_city_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GPS Auto-Detect Button
                Surface(
                    onClick = {
                        onSelectGpsLocation()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isUsingGps) BrandTeal.copy(alpha = 0.18f) else Color(0xFF1E293B),
                    border = if (isUsingGps) androidx.compose.foundation.BorderStroke(1.5.dp, BrandTeal) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("city_dialog_gps_button")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Current GPS",
                                tint = if (isUsingGps) BrandTeal else Color.LightGray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Current Device GPS 🛰️",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Auto-fetch real spots around your live coordinates",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (isUsingGps) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = BrandTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search any city in the world...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = BrandTeal
                        )
                    },
                    trailingIcon = {
                        if (isSearchingGeocode) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BrandTeal,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("city_search_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section Label
                Text(
                    text = if (geocodedResults.isNotEmpty()) "Search Results" else "Popular World Destinations",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = BrandTeal,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // List / Grid
                if (geocodedResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(geocodedResults, key = { it.id }) { city ->
                            CityListItem(
                                city = city,
                                isSelected = !isUsingGps && selectedCity?.name == city.name,
                                onClick = {
                                    onSelectCity(city)
                                    onDismiss()
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val filteredPopular = popularCities.filter {
                            searchQuery.isBlank() ||
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.country.contains(searchQuery, ignoreCase = true)
                        }

                        items(filteredPopular, key = { it.id }) { city ->
                            CityGridCard(
                                city = city,
                                isSelected = !isUsingGps && selectedCity?.id == city.id,
                                onClick = {
                                    onSelectCity(city)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CityGridCard(
    city: GlobalCity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Secondary.copy(alpha = 0.2f) else Color(0xFF1E293B),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Secondary) else null,
        modifier = Modifier.testTag("city_card_${city.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = city.flagEmoji, fontSize = 24.sp)
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = Secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = city.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = city.country,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CityListItem(
    city: GlobalCity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BrandTeal.copy(alpha = 0.2f) else Color(0xFF1E293B),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, BrandTeal) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = city.flagEmoji, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = city.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = city.country,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Select Location",
                tint = BrandTeal,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
