package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixedDim
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import com.example.ui.theme.TertiaryContainer
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable animated linear shimmer brush that smoothly sweeps across skeleton components.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerColors = listOf(
        SurfaceContainerLow.copy(alpha = 0.9f),
        SurfaceContainerHigh.copy(alpha = 0.5f),
        SurfaceContainerLowest.copy(alpha = 0.95f),
        SurfaceContainerHigh.copy(alpha = 0.5f),
        SurfaceContainerLow.copy(alpha = 0.9f)
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * 'Discover Gems' loading state component presented while GeminiQueryService / GeminiSpotService
 * generates fresh, verified tourist recommendations for the user's active GPS coordinates.
 */
@Composable
fun DiscoverGemsLoadingState(
    category: String = "all",
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    gpsAccuracyMeters: Float? = null,
    statusMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover_gems_loading_state"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Radar Scanner & Gemini AI Badge Header Card
        PulsingRadarGeminiHeader(
            category = category,
            userCoordinates = userCoordinates,
            gpsAccuracyMeters = gpsAccuracyMeters
        )

        // 2. Rotating Scouting Tips & Real-Time Telemetry Ticker
        ScoutingTelemetryTicker(
            category = category,
            statusMessage = statusMessage,
            userCoordinates = userCoordinates
        )

        // 3. Shimmer Skeleton Recommendation Cards
        repeat(2) { index ->
            ShimmerSkeletonSpotCard(
                shimmerBrush = shimmerBrush,
                modifier = Modifier.testTag("shimmer_skeleton_card_$index")
            )
        }
    }
}

/**
 * Pulsing Radar Header with animated concentric scan waves and Gemini 3.8 Flash badge.
 */
@Composable
fun PulsingRadarGeminiHeader(
    category: String,
    userCoordinates: Pair<Double, Double>,
    gpsAccuracyMeters: Float?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radar_sweep_transition")

    // Continuous 360 degree beam rotation
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep_angle"
    )

    // Pulsing expanding wave 1
    val wave1Radius by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1_radius"
    )
    val wave1Alpha by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1_alpha"
    )

    // Pulsing expanding wave 2 (staggered)
    val wave2Radius by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2_radius"
    )
    val wave2Alpha by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2_alpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = BrandNight,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pulsing_radar_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gemini AI Pill Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Tertiary.copy(alpha = 0.85f),
                                PrimaryContainer.copy(alpha = 0.9f),
                                BrandTeal.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(BrandNight.copy(alpha = 0.88f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Gemini AI",
                        tint = BrandTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gemini 3.8 Flash AI Discovery",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Canvas Radar with Concentric Circles & Sweep Beam
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .testTag("radar_pulse_canvas"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Background concentric grid rings
                    val rings = listOf(0.33f, 0.66f, 1.0f)
                    rings.forEach { ratio ->
                        drawCircle(
                            color = SecondaryFixedDim.copy(alpha = 0.22f),
                            radius = maxRadius * ratio,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    // Crosshair guides
                    drawLine(
                        color = SecondaryFixedDim.copy(alpha = 0.18f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = SecondaryFixedDim.copy(alpha = 0.18f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Expanding Pulse Wave 1
                    drawCircle(
                        color = BrandTeal.copy(alpha = wave1Alpha),
                        radius = maxRadius * wave1Radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Expanding Pulse Wave 2
                    drawCircle(
                        color = PrimaryContainer.copy(alpha = wave2Alpha),
                        radius = maxRadius * wave2Radius,
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx())
                    )

                    // Radar Sweep Beam
                    val rad = Math.toRadians(sweepAngle.toDouble())
                    val endX = center.x + (maxRadius * cos(rad)).toFloat()
                    val endY = center.y + (maxRadius * sin(rad)).toFloat()

                    // Radar sweep trailing gradient sector
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                BrandTeal.copy(alpha = 0.05f),
                                BrandTeal.copy(alpha = 0.25f)
                            ),
                            center = center
                        ),
                        startAngle = sweepAngle - 45f,
                        sweepAngle = 45f,
                        useCenter = true,
                        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                        size = Size(maxRadius * 2f, maxRadius * 2f)
                    )

                    // Beam leading edge line
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, BrandTeal),
                            start = center,
                            end = Offset(endX, endY)
                        ),
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Central GPS Pulse Node
                    drawCircle(
                        color = BrandTeal.copy(alpha = 0.35f),
                        radius = 12.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = center
                    )
                }

                // Center Icon Overlay
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = "Radar Ping",
                    tint = BrandTeal.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle & Status
            Text(
                text = "Discovering Hidden Gems & Landmarks",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Live Telemetry GPS Chip
            val coordText = String.format(Locale.US, "%.4f°N, %.4f°W", userCoordinates.first, userCoordinates.second)
            val accText = if (gpsAccuracyMeters != null) " (±${gpsAccuracyMeters.toInt()}m)" else ""
            Text(
                text = "📍 $coordText$accText • ${category.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall,
                color = BrandTeal.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Animated Scouting Telemetry Ticker cycling through contextual travel scouting tips.
 */
@Composable
fun ScoutingTelemetryTicker(
    category: String,
    statusMessage: String?,
    userCoordinates: Pair<Double, Double>,
    modifier: Modifier = Modifier
) {
    val tips = remember(category) {
        listOf(
            "Querying Gemini 3.8 Flash for verified architectural and historic landmarks...",
            "Scouting authentic local pastelarias & artisan eateries nearby...",
            "Cross-referencing panoramic viewpoints & sunset miradouros...",
            "Filtering pedestrian-accessible hidden alleys and cobblestone lanes...",
            "Synthesizing immersive audio lore narratives for local heritage spots...",
            "Verifying official cultural designations and opening hours...",
            "Eliminating duplicate nodes and generic map placeholders..."
        )
    }

    var tipIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .testTag("scouting_telemetry_ticker")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE AI SCOUTING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = Primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Synthesizing",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Secondary
                    )
                }
            }

            // Animated Rotating Tip Text
            AnimatedContent(
                targetState = statusMessage ?: tips[tipIndex],
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(300))) togetherWith
                            (slideOutVertically { height -> -height / 2 } + fadeOut(tween(300)))
                },
                label = "tip_rotation"
            ) { tipText ->
                Text(
                    text = "💡 $tipText",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Shimmer skeleton card matching the exact layout of ScoutSpotCard.
 */
@Composable
fun ShimmerSkeletonSpotCard(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Skeleton Image Placeholder (180dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(shimmerBrush)
            ) {
                // Top Left: Skeleton Category Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(shimmerBrush)
                    )
                }

                // Top Right: Skeleton Heart Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )

                // Bottom Left: Skeleton Distance Chip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .width(110.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmerBrush)
                )
            }

            // Skeleton Content Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title and Rating Skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(shimmerBrush)
                    )
                }

                // Description Skeleton Lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action Bar Skeleton (Audio pill + upvote count)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(shimmerBrush)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        }
    }
}
