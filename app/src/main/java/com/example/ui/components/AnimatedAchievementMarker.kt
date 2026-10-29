package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.AchievementEntity
import com.example.data.local.ScoutSpotEntity
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.util.ImageResolver
import com.example.util.MarkerTier
import com.example.util.MarkerTierResolver

/**
 * Animated Map Marker component that visually transforms based on user achievement progress toward
 * unlocking category badges. Features animated pulsing aura rings, spring bounce tap feedback, and
 * achievement status tooltip popovers.
 */
@Composable
fun AnimatedAchievementMarker(
    spot: ScoutSpotEntity,
    achievements: List<AchievementEntity>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tierStyle = remember(spot.category, achievements) {
        MarkerTierResolver.resolveTierForCategory(spot.category, achievements)
    }

    var isTapped by remember { mutableStateOf(false) }

    // Spring Bounce scale animation on tap
    val springScale by animateFloatAsState(
        targetValue = if (isTapped || isSelected) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { isTapped = false },
        label = "marker_bounce"
    )

    // Pulsing Aura Ring animation for Bronze, Silver, Gold tiers
    val infiniteTransition = rememberInfiniteTransition(label = "marker_aura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag("animated_marker_${spot.id}")
            .clickable {
                isTapped = true
                onClick()
            }
    ) {
        // Tooltip Popover when selected
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xF00F172A),
                contentColor = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .border(1.dp, tierStyle.auraColor.takeIf { it != Color.Transparent } ?: BrandCoral, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tierStyle.tier != MarkerTier.STANDARD) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = tierStyle.auraColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = spot.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tierStyle.badgeLabel} • ★ ${spot.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tierStyle.auraColor.takeIf { it != Color.Transparent } ?: BrandTeal,
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Animated Pin Base
        Box(
            modifier = Modifier
                .scale(springScale)
                .size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated Aura Pulse Ring
            if (tierStyle.isPulsing) {
                Box(
                    modifier = Modifier
                        .scale(auraScale)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(tierStyle.auraColor.copy(alpha = auraAlpha))
                )
            }

            // Core Marker Body
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) BrandCoral
                        else tierStyle.auraColor.takeIf { it != Color.Transparent } ?: BrandTeal
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(ImageResolver.getDrawableResForCategory(spot.category))
                        .crossfade(true)
                        .build(),
                    contentDescription = spot.title,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                )

                // Sparkle indicator for Silver & Gold tiers
                if (tierStyle.showSparkle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tierStyle.auraColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            }
        }
    }
}
