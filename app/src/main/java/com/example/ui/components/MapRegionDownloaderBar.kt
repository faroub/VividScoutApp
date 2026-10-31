package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.offline.GeoBoundingBox
import com.example.ui.theme.BrandTeal

@Composable
fun MapRegionDownloaderBar(
    selectionBounds: GeoBoundingBox,
    userLat: Double,
    userLng: Double,
    panOffset: Offset,
    activeZoomScale: Float,
    baseScale: Float,
    onUpdateSelectionBounds: ((GeoBoundingBox) -> Unit)?,
    onCancelRegionDownload: (() -> Unit)?,
    onConfirmRegionDownload: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF2080B1E),
        border = BorderStroke(1.5.dp, BrandTeal.copy(alpha = 0.85f)),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("region_download_bar")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Offline Area",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // Dimension Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(BrandTeal.copy(alpha = 0.2f))
                        .border(1.dp, BrandTeal.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = selectionBounds.formattedDimensions,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BrandTeal,
                        fontSize = 11.sp
                    )
                }
            }

            // Adjustment controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shrink (-20%)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable {
                            val cLat = selectionBounds.centerLat
                            val cLng = selectionBounds.centerLng
                            val halfH = (selectionBounds.maxLat - selectionBounds.minLat) * 0.4
                            val halfW = (selectionBounds.maxLng - selectionBounds.minLng) * 0.4
                            onUpdateSelectionBounds?.invoke(
                                GeoBoundingBox(cLat - halfH, cLng - halfW, cLat + halfH, cLng + halfW)
                            )
                        }
                        .padding(vertical = 6.dp)
                        .testTag("region_download_shrink_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "- Shrink Area",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                // Expand (+20%)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable {
                            val cLat = selectionBounds.centerLat
                            val cLng = selectionBounds.centerLng
                            val halfH = (selectionBounds.maxLat - selectionBounds.minLat) * 0.6
                            val halfW = (selectionBounds.maxLng - selectionBounds.minLng) * 0.6
                            onUpdateSelectionBounds?.invoke(
                                GeoBoundingBox(cLat - halfH, cLng - halfW, cLat + halfH, cLng + halfW)
                            )
                        }
                        .padding(vertical = 6.dp)
                        .testTag("region_download_expand_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Expand Area",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                // Re-center on map view center
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable {
                            val dLng = -panOffset.x / (baseScale * activeZoomScale)
                            val dLat = panOffset.y / (baseScale * activeZoomScale)
                            val newCenterLat = userLat + dLat
                            val newCenterLng = userLng + dLng
                            val halfH = (selectionBounds.maxLat - selectionBounds.minLat) / 2.0
                            val halfW = (selectionBounds.maxLng - selectionBounds.minLng) / 2.0
                            onUpdateSelectionBounds?.invoke(
                                GeoBoundingBox(
                                    newCenterLat - halfH,
                                    newCenterLng - halfW,
                                    newCenterLat + halfH,
                                    newCenterLng + halfW
                                )
                            )
                        }
                        .padding(vertical = 6.dp)
                        .testTag("region_download_recenter_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Center Here",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            // Bottom Action Row: Cancel / Download
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onCancelRegionDownload?.invoke() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("region_download_cancel_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Button(
                    onClick = { onConfirmRegionDownload?.invoke() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color(0xFF080B1E)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("region_download_confirm_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Download This Area",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
