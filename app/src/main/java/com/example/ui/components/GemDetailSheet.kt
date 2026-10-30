package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.util.ImageResolver
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceString
import com.example.data.model.WeatherReport
import com.example.data.repository.WeatherRepository
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import kotlinx.coroutines.launch

import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.data.local.CommunityTipEntity
import com.example.util.TipDecayUtils
import com.example.util.TipDecayTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemDetailSheet(
    isOpen: Boolean,
    spot: ScoutSpotEntity?,
    onDismiss: () -> Unit,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    onToggleSave: (ScoutSpotEntity) -> Unit = {},
    onRoutePlan: (ScoutSpotEntity) -> Unit = {},
    onPostCommunityTip: ((spotId: String, spotTitle: String, content: String, isStatus: Boolean, category: String) -> Unit)? = null,
    onUpvoteCommunityTip: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!isOpen || spot == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var isPostTipOpen by remember { mutableStateOf(false) }

    val db = remember { AppDatabase.getDatabase(context) }
    val tipsForSpot by db.communityTipDao().getTipsForSpotFlow(spot.id).collectAsState(initial = emptyList())
    val latestStatus = tipsForSpot.firstOrNull { it.isStatusUpdate }

    var weatherReport by remember { mutableStateOf<WeatherReport?>(null) }
    var isWeatherLoading by remember { mutableStateOf(false) }

    val weatherRepository = remember {
        val db = AppDatabase.getDatabase(context)
        WeatherRepository(db.weatherDao())
    }

    fun fetchWeather(forceRefresh: Boolean = false) {
        scope.launch {
            isWeatherLoading = true
            weatherReport = weatherRepository.getOrFetchWeather(
                gemId = spot.id,
                latitude = spot.latitude,
                longitude = spot.longitude,
                category = spot.category,
                forceRefresh = forceRefresh
            )
            isWeatherLoading = false
        }
    }

    LaunchedEffect(spot.id) {
        fetchWeather(forceRefresh = false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        modifier = modifier.testTag("gem_detail_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Image Bleed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E293B))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ImageResolver.resolveImageModel(spot.imageUrl, spot.category, spot.title))
                        .crossfade(true)
                        .placeholder(ImageResolver.getDrawableResForCategory(spot.category))
                        .error(ImageResolver.getDrawableResForCategory(spot.category))
                        .fallback(ImageResolver.getDrawableResForCategory(spot.category))
                        .build(),
                    contentDescription = spot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("close_gem_sheet_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Top Bookmark
                IconButton(
                    onClick = { onToggleSave(spot) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("bookmark_gem_sheet_btn")
                ) {
                    Icon(
                        imageVector = if (spot.isSaved) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Bookmark",
                        tint = if (spot.isSaved) BrandCoral else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bottom Overlay Distance & Location
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val distStr = spot.computedDistanceString(userCoordinates.first, userCoordinates.second)
                        Text(
                            text = "$distStr • ${spot.locationName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                // Photo attribution badge
                val photoAttribution = ImageResolver.getAttribution(spot.title, spot.imageUrl, spot.category)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = photoAttribution,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // Spot Title and Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spot.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "${spot.category.replaceFirstChar { it.uppercase() }} • ${spot.badgeLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandTeal
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${spot.rating}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            // Integrated Weather & Visit Suitability Card
            WeatherCard(
                weather = weatherReport,
                isLoading = isWeatherLoading,
                onRefreshClick = { fetchWeather(forceRefresh = true) }
            )

            // Live Community Spot Status Banner (if recent status exists)
            if (latestStatus != null) {
                val decayTier = TipDecayUtils.calculateDecayTier(latestStatus.timestamp)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = decayTier.containerColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, decayTier.dotColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gem_detail_live_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(decayTier.dotColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = decayTier.dotColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "LIVE STATUS ALERT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 9.sp
                                    ),
                                    color = decayTier.contentColor
                                )
                                Text(
                                    text = "• ${TipDecayUtils.formatRelativeTime(latestStatus.timestamp)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = decayTier.contentColor.copy(alpha = 0.8f),
                                    fontSize = 9.sp
                                )
                            }
                            Text(
                                text = latestStatus.content,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Description
            Text(
                text = spot.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )

            // Pro Tip Callout
            if (!spot.proTip.isNull_or_blank()) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Pro Tip",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Local Scout Tip",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = spot.proTip.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Community Tips & Ground Truth Section
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gem_detail_community_section")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "COMMUNITY GROUND TRUTH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = BrandTeal
                            )
                            Text(
                                text = "${tipsForSpot.size} Scout Reports in P2P Cache",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = { isPostTipOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("gem_detail_post_tip_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Leave Tip",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (tipsForSpot.isEmpty()) {
                        Text(
                            text = "No community reports for this gem yet. Leave the first tip or status alert!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tipsForSpot.take(3).forEach { tip ->
                                CommunityTipCard(
                                    tip = tip,
                                    onUpvoteClick = { tipId ->
                                        if (onUpvoteCommunityTip != null) {
                                            onUpvoteCommunityTip(tipId)
                                        } else {
                                            scope.launch {
                                                db.communityTipDao().incrementUpvote(tipId)
                                            }
                                        }
                                    },
                                    showSpotHeader = false
                                )
                            }
                        }
                    }
                }
            }

            // Action Button: Route to Gem
            Button(
                onClick = {
                    onDismiss()
                    onRoutePlan(spot)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandCoral),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("plan_route_to_gem_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Plan Offline Route to Gem",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    PostTipDialog(
        isOpen = isPostTipOpen,
        onDismiss = { isPostTipOpen = false },
        onSubmit = { spotId, spotTitle, content, isStatus, category ->
            if (onPostCommunityTip != null) {
                onPostCommunityTip(spotId, spotTitle, content, isStatus, category)
            } else {
                scope.launch {
                    val repo = com.example.data.repository.CommunityTipRepository(db.communityTipDao())
                    repo.postTip(spotId, spotTitle, content, isStatus, category)
                }
            }
        },
        initialSpot = spot
    )
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.trim().isEmpty()
}
