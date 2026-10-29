package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SuitabilityLevel
import com.example.data.model.WeatherReport

@Composable
fun WeatherCard(
    weather: WeatherReport?,
    isLoading: Boolean = false,
    onRefreshClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF161F33),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D5E)),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_info_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header Row: Weather Title + Offline Cache Badge + Refresh Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = "Weather",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Weather & Visit Conditions",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = weather?.condition ?: "Checking conditions...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (weather != null) {
                        // Cache Age / Offline Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (weather.isOffline) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                    else Color(0xFF10B981).copy(alpha = 0.2f)
                                )
                                .border(
                                    1.dp,
                                    if (weather.isOffline) Color(0xFFF59E0B) else Color(0xFF10B981),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("weather_cache_age_badge")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (weather.isOffline) Icons.Default.CloudOff else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (weather.isOffline) Color(0xFFFBBF24) else Color(0xFF34D399),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = weather.cacheAgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (weather.isOffline) Color(0xFFFBBF24) else Color(0xFF34D399),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("weather_refresh_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh weather",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading && weather == null) {
                // Skeleton loading state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF38BDF8),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fetching live weather forecast...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            } else if (weather != null) {
                // Metrics Row: Temp, Feels-Like, Precip Chance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Temperature & Feels-Like Box
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val condLower = weather.condition.lowercase()
                            val icon = when {
                                condLower.contains("rain") || condLower.contains("shower") || condLower.contains("drizzle") -> Icons.Default.Grain
                                condLower.contains("cloud") || condLower.contains("fog") -> Icons.Default.Cloud
                                else -> Icons.Default.WbSunny
                            }
                            val iconTint = when {
                                condLower.contains("rain") || condLower.contains("shower") -> Color(0xFF38BDF8)
                                condLower.contains("cloud") -> Color(0xFFCBD5E1)
                                else -> Color(0xFFFBBF24)
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = weather.condition,
                                tint = iconTint,
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "${weather.temperatureC.toInt()}°C",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Feels like ${weather.feelsLikeC.toInt()}°C",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    // Precipitation Probability Box
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = "Precipitation",
                                tint = if (weather.precipitationChance >= 30) Color(0xFF38BDF8) else Color(0xFF64748B),
                                modifier = Modifier.size(26.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "${weather.precipitationChance}%",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Precipitation risk",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Smart Visit Suitability Assessment Banner
                val (badgeBg, badgeFg, borderCol, icon) = when (weather.suitability) {
                    SuitabilityLevel.IDEAL -> Quadruple(Color(0xFF064E3B), Color(0xFF34D399), Color(0xFF059669), Icons.Default.CheckCircle)
                    SuitabilityLevel.CAUTION -> Quadruple(Color(0xFF78350F), Color(0xFFFBBF24), Color(0xFFD97706), Icons.Default.Warning)
                    SuitabilityLevel.UNFAVORABLE -> Quadruple(Color(0xFF1E1B4B), Color(0xFFA78BFA), Color(0xFF7C3AED), Icons.Default.Info)
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_suitability_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = weather.suitability.toLabel(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = badgeFg
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = weather.suitabilityReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
