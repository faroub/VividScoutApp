package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.viewmodel.ZoomPreset

@Composable
fun MapZoomPresetSelector(
    zoomPreset: ZoomPreset?,
    activeZoomScale: Float,
    onSetZoomPreset: (ZoomPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZoomPreset.entries.forEach { preset ->
            val isSelected = (zoomPreset == preset) || Math.abs(preset.scale - activeZoomScale) < 0.1f
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) BrandTeal else Color(0xF2080B1E))
                    .border(
                        1.dp,
                        if (isSelected) BrandTeal else Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onSetZoomPreset(preset) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("zoom_preset_chip_${preset.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

@Composable
fun MapCanvasControls(
    isRegionDownloadMode: Boolean,
    onStartRegionDownload: (() -> Unit)?,
    onCancelRegionDownload: (() -> Unit)?,
    onPlanTrailClick: (() -> Unit)?,
    onResetPan: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Region Downloader Button (48dp Touch Target)
        if (onStartRegionDownload != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isRegionDownloadMode) BrandTeal else Color(0xFF080B1E).copy(alpha = 0.95f))
                    .border(1.dp, if (isRegionDownloadMode) Color.White else BrandTeal, CircleShape)
                    .clickable {
                        if (isRegionDownloadMode) {
                            onCancelRegionDownload?.invoke()
                        } else {
                            onStartRegionDownload()
                        }
                    }
                    .testTag("vector_region_download_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = if (isRegionDownloadMode) "Exit Region Downloader" else "Download Offline Region Map",
                    tint = if (isRegionDownloadMode) Color.Black else BrandTeal,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Offline Walking Route Planner Button (48dp Touch Target)
        if (onPlanTrailClick != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.95f))
                    .border(1.dp, BrandCoral, CircleShape)
                    .clickable { onPlanTrailClick() }
                    .testTag("vector_route_planner_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = "Plan Walking Route",
                    tint = BrandCoral,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Center on GPS Beacon (48dp Touch Target)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF080B1E).copy(alpha = 0.95f))
                .border(1.dp, BrandTeal, CircleShape)
                .clickable { onResetPan() }
                .testTag("vector_center_on_me"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Center Map on Current Location",
                tint = BrandTeal,
                modifier = Modifier.size(22.dp)
            )
        }

        // Step Zoom In (+) (48dp Touch Target)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF080B1E).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable { onZoomIn() }
                .testTag("vector_zoom_in"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Zoom In Map",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Step Zoom Out (-) (48dp Touch Target)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF080B1E).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable { onZoomOut() }
                .testTag("vector_zoom_out"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Zoom Out Map",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
