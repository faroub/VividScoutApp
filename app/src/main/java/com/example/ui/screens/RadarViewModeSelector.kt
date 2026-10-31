package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SurfaceContainerLowest

@Composable
fun RadarViewModeSelector(
    viewMode: RadarViewMode,
    onViewModeChange: (RadarViewMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val isMap = viewMode == RadarViewMode.MAP
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isMap) PrimaryContainer else Color.Transparent)
                    .clickable { onViewModeChange(RadarViewMode.MAP) }
                    .padding(vertical = 10.dp)
                    .testTag("mode_toggle_map"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = if (isMap) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live Gem Map",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isMap) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isRadar = viewMode == RadarViewMode.RADAR
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isRadar) PrimaryContainer else Color.Transparent)
                    .clickable { onViewModeChange(RadarViewMode.RADAR) }
                    .padding(vertical = 10.dp)
                    .testTag("mode_toggle_radar"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (isRadar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "On-Foot Radar",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isRadar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RadarRadiusSelector(
    selectedRadius: String,
    onRadiusSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        listOf("200m", "500m", "1km", "3km", "All").forEach { radius ->
            val isSelected = selectedRadius == radius
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) PrimaryContainer else SurfaceContainerLowest)
                    .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(999.dp))
                    .clickable { onRadiusSelected(radius) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("radius_pill_$radius")
            ) {
                Text(
                    text = radius,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
