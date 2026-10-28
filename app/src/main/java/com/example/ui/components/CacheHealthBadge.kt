package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VicinityCacheHealth

/**
 * Cache Health Score Badge overlaying the main map view.
 * Displays the current percentage of local Room database gems in the user's immediate vicinity
 * vs. what needs to be fetched from OpenStreetMap Overpass API.
 */
@Composable
fun CacheHealthBadge(
    health: VicinityCacheHealth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val levelColor = Color(health.level.colorHex)
    val interactionSource = remember { MutableInteractionSource() }

    val infiniteTransition = rememberInfiniteTransition(label = "cacheHealthPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xF2080B1E),
        border = BorderStroke(1.2.dp, levelColor.copy(alpha = pulseAlpha)),
        shadowElevation = 6.dp,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = levelColor),
                onClickLabel = "View vicinity cache health diagnostics",
                onClick = onClick
            )
            .testTag(if (isCompact) "cache_health_badge_compact" else "cache_health_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Icon / Status indicator
            if (health.isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = levelColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(levelColor.copy(alpha = 0.2f))
                        .border(1.dp, levelColor.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Cache Health Icon",
                        tint = levelColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Score percentage
            Text(
                text = "${health.scorePercent}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = levelColor,
                fontSize = 11.sp
            )

            if (!isCompact) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = health.level.badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
