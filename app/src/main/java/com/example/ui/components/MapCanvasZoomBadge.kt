package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandTeal
import com.example.ui.viewmodel.ZoomPreset

@Composable
internal fun MapCanvasZoomBadge(
    activeZoomScale: Float,
    zoomPreset: ZoomPreset?,
    modifier: Modifier = Modifier
) {
    // Live Zoom Percentage & Preset Badge (Top Floating on Canvas)
    val zoomPct = (activeZoomScale * 100).toInt()
    val presetText = zoomPreset?.label ?: when {
        activeZoomScale <= 0.8f -> "City View"
        activeZoomScale <= 1.8f -> "Neighborhood"
        activeZoomScale <= 3.0f -> "Street Level"
        else -> "Close-Up Gem"
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xF2080B1E),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$zoomPct% • $presetText",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}
