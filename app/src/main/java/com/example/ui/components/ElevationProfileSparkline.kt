package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terrain
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ElevationPoint
import com.example.engine.RouteElevationProfile
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import kotlin.math.roundToInt

/**
 * Interactive Sparkline Elevation Profile component for RoutePlannerSheet.
 * Visualizes incline vs. decline along the walking route with dynamic color gradient,
 * total ascent/descent metrics, intensity rating, and touch scrubbing for grade inspection.
 */
@Composable
fun ElevationProfileSparkline(
    elevationProfile: RouteElevationProfile?,
    modifier: Modifier = Modifier
) {
    if (elevationProfile == null || elevationProfile.points.size < 2) return

    val points = elevationProfile.points
    val minElev = elevationProfile.minElevationMeters
    val maxElev = elevationProfile.maxElevationMeters
    val elevRange = (maxElev - minElev).coerceAtLeast(4.0)
    val totalDist = points.last().distanceMeters.coerceAtLeast(1.0)

    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("elevation_profile_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title & Intensity Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BrandCoral.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terrain,
                            contentDescription = "Elevation Topography",
                            tint = BrandCoral,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Elevation Profile",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Incline & decline across Lisbon's hills",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                // Intensity Badge
                val (badgeBg, badgeFg) = when {
                    elevationProfile.totalAscentMeters >= 40.0 -> Pair(Color(0xFF7F1D1D), Color(0xFFFCA5A5))
                    elevationProfile.totalAscentMeters >= 20.0 -> Pair(Color(0xFF78350F), Color(0xFFFDE68A))
                    else -> Pair(Color(0xFF064E3B), Color(0xFF6EE7B7))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("elevation_intensity_badge")
                ) {
                    Text(
                        text = elevationProfile.intensityLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Badges: Ascent, Descent, Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Climb (Ascent)
                Surface(
                    color = BrandCoral.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandCoral.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "Ascent",
                            tint = BrandCoral,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("CLIMB", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BrandCoral)
                            Text(elevationProfile.formattedAscent, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Total Descent
                Surface(
                    color = BrandTeal.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = "Descent",
                            tint = BrandTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("DESCENT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BrandTeal)
                            Text(elevationProfile.formattedDescent, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Elevation Range
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Elevation Span",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("SPAN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            Text(elevationProfile.formattedMinMax, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas Sparkline Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(points) {
                        detectDragGestures { change, _ ->
                            scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                    .testTag("elevation_sparkline_canvas")
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val topPadding = 12f
                    val bottomPadding = 16f
                    val chartHeight = height - topPadding - bottomPadding

                    // Map points to canvas offsets
                    val offsets = points.map { pt ->
                        val x = (pt.distanceMeters / totalDist).toFloat() * width
                        val normY = ((maxElev - pt.elevationMeters) / elevRange).toFloat().coerceIn(0f, 1f)
                        val y = topPadding + normY * chartHeight
                        Offset(x, y)
                    }

                    if (offsets.size >= 2) {
                        // 1. Shaded area under the curve
                        val areaPath = Path().apply {
                            moveTo(offsets.first().x, height - bottomPadding)
                            offsets.forEach { pt -> lineTo(pt.x, pt.y) }
                            lineTo(offsets.last().x, height - bottomPadding)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    BrandCoral.copy(alpha = 0.30f),
                                    BrandTeal.copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                startY = topPadding,
                                endY = height
                            )
                        )

                        // 2. Base zero-line / guide rule
                        drawLine(
                            color = Color(0xFF334155),
                            start = Offset(0f, height - bottomPadding),
                            end = Offset(width, height - bottomPadding),
                            strokeWidth = 1f
                        )

                        // 3. Segmented Gradient Sparkline Stroke
                        for (i in 0 until offsets.size - 1) {
                            val p1 = offsets[i]
                            val p2 = offsets[i + 1]
                            val grade = points[i].slopeGradePct

                            // Color-code: Coral for uphill climbs (> +1.5%), Teal for downhill / flat (< 0%)
                            val strokeColor = when {
                                grade >= 4.0 -> BrandCoral
                                grade >= 0.5 -> Color(0xFFFB923C) // Warm orange moderate climb
                                grade <= -1.0 -> BrandTeal // Cool teal descent
                                else -> Color(0xFF38BDF8) // Sky blue flat
                            }

                            drawLine(
                                color = strokeColor,
                                start = p1,
                                end = p2,
                                strokeWidth = 3.5f,
                                cap = StrokeCap.Round
                            )
                        }

                        // 4. Start (Origin) Anchor Dot
                        drawCircle(
                            color = BrandTeal,
                            radius = 4.5f,
                            center = offsets.first()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2f,
                            center = offsets.first()
                        )

                        // 5. Destination Anchor Dot
                        drawCircle(
                            color = BrandCoral,
                            radius = 5.5f,
                            center = offsets.last()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5f,
                            center = offsets.last()
                        )

                        // 6. Active Touch Scrubber
                        scrubFraction?.let { frac ->
                            val scrubX = frac * width
                            val nearestIdx = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                            val scrubOffset = offsets[nearestIdx]

                            // Vertical guideline
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(scrubOffset.x, topPadding),
                                end = Offset(scrubOffset.x, height - bottomPadding),
                                strokeWidth = 1.5f
                            )

                            // Scrubber indicator ring
                            drawCircle(
                                color = Color.White,
                                radius = 6f,
                                center = scrubOffset
                            )
                            drawCircle(
                                color = BrandCoral,
                                radius = 4f,
                                center = scrubOffset
                            )
                        }
                    }
                }
            }

            // Scrubber telemetry readout or Min/Max guide
            val activeScrub = scrubFraction?.let { frac ->
                val nearestIdx = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                points[nearestIdx]
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeScrub != null) {
                    val gradeText = if (activeScrub.slopeGradePct >= 0) {
                        String.format(java.util.Locale.US, "+%.1f%% grade", activeScrub.slopeGradePct)
                    } else {
                        String.format(java.util.Locale.US, "%.1f%% grade", activeScrub.slopeGradePct)
                    }

                    Text(
                        text = "At ${activeScrub.formattedDistance}: ${activeScrub.formattedElevation} ($gradeText)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (activeScrub.slopeGradePct >= 3.0) BrandCoral else BrandTeal,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = "Start: ${points.first().formattedElevation}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Tap or drag graph to inspect grades",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Finish: ${points.last().formattedElevation}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
