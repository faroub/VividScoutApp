package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
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
import com.example.engine.OfflineWalkingTrail
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal

@Composable
fun MapRouteHud(
    activeTrail: OfflineWalkingTrail,
    onPlanTrailClick: (() -> Unit)?,
    onClearTrailClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF2080B1E),
        border = BorderStroke(1.dp, BrandCoral.copy(alpha = 0.6f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("vector_route_hud")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BrandCoral.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = BrandCoral,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${activeTrail.originTitle} → ${activeTrail.destinationTitle}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${activeTrail.formattedDistance} • ${activeTrail.formattedDuration}" +
                            (activeTrail.steps.firstOrNull()?.let { " • ${it.instruction}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (onPlanTrailClick != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandTeal.copy(alpha = 0.25f))
                        .border(1.dp, BrandTeal.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { onPlanTrailClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("vector_route_steps_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Steps",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
            if (onClearTrailClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onClearTrailClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("vector_route_clear_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Route",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
