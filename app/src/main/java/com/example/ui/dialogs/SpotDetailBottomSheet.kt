package com.example.ui.dialogs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.Secondary
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import com.example.ui.theme.LocalAppLanguage
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.util.ImageResolver
import com.example.data.local.ScoutSpotEntity
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.ui.components.CommunityTipCard
import com.example.ui.components.PostTipDialog
import com.example.util.TipDecayUtils
import com.example.ui.theme.BrandTeal
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.VividScoutApplication
import com.example.data.local.AppDatabase
import com.example.data.model.WeatherReport
import com.example.data.repository.SpotPhotosState
import com.example.data.repository.CommunityTipRepository
import com.example.data.repository.SpotPhotoData
import com.example.data.repository.WeatherRepository
import com.example.di.AppContainer
import com.example.ui.components.SourceAttributionRow
import com.example.ui.components.WeatherCard
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import com.example.util.OpeningHoursParser
import com.example.util.OpeningStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailBottomSheet(
    spot: ScoutSpotEntity,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleDiscovered: () -> Unit = {},
    onSaveNote: (String) -> Unit = {},
    onUpvote: () -> Unit,
    onToggleAudio: () -> Unit,
    isPlayingAudio: Boolean,
    onPlanRoute: (() -> Unit)? = null,
    onPostTip: ((spotId: String, spotTitle: String, content: String, isStatus: Boolean, category: String) -> Unit)? = null,
    onReFetchImage: (spotId: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onUploadPhoto: (suspend (spotId: String, uri: Uri) -> Result<SpotPhotoData>)? = null,
    onReportPhoto: (suspend (photoId: String) -> Result<Int>)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isUploadingPhoto by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var reportStatusMessage by remember { mutableStateOf<String?>(null) }

    var noteText by remember(spot.id) { mutableStateOf(spot.userNotes ?: "") }
    var isNoteSaved by remember { mutableStateOf(false) }

    var weatherReport by remember { mutableStateOf<WeatherReport?>(null) }
    var isWeatherLoading by remember { mutableStateOf(false) }

    val weatherRepository = remember {
        val db = AppDatabase.getDatabase(context)
        WeatherRepository(db.weatherDao())
    }

    var isPostTipOpen by remember { mutableStateOf(false) }
    val db = remember { AppDatabase.getDatabase(context) }
    val tipsForSpot by db.communityTipDao().getTipsForSpotFlow(spot.id).collectAsState(initial = emptyList())
    val latestStatus = tipsForSpot.firstOrNull { it.isStatusUpdate }

    val appContainer = (context.applicationContext as? VividScoutApplication)?.container
    // The photo stream distinguishes loaded photos, an empty result, and listener
    // failure (e.g. a missing Firestore index) — a failed listener must never render
    // as an empty photo grid as if the spot simply had no photos.
    val spotPhotosState by remember(spot.id) {
        appContainer?.photoModerationRepository?.photosForSpotFlow(spot.id)
            ?: flowOf(SpotPhotosState.Loaded(emptyList()))
    }.collectAsState(initial = SpotPhotosState.Loading)
    val visibleSpotPhotos = when (val state = spotPhotosState) {
        is SpotPhotosState.Loaded -> state.photos.filter { !it.isHidden }
        else -> emptyList()
    }
    val photosErrorMessage = (spotPhotosState as? SpotPhotosState.Error)?.let {
        "Community photos are unavailable${it.throwable.message?.let { m -> ": $m" } ?: "."}"
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri: Uri? ->
        // Cancellation (null Uri) must never be reported as an action or success.
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isUploadingPhoto = true
            uploadStatusMessage = null
            val uploadResult = onUploadPhoto?.invoke(spot.id, pickedUri)
            isUploadingPhoto = false
            uploadStatusMessage = when {
                uploadResult == null -> "Photo upload unavailable."
                uploadResult.isSuccess -> "Photo uploaded successfully!"
                else -> uploadResult.exceptionOrNull()?.message
                    ?: "Photo upload failed. Please try again."
            }
        }
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
        containerColor = SurfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("spot_detail_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Hero Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ImageResolver.resolveImageModel(spot.imageUrl, spot.category, spot.title, spot.id))
                        .crossfade(true)
                        .placeholder(ImageResolver.getDrawableResForCategory(spot.category))
                        .error(ImageResolver.getDrawableResForCategory(spot.category))
                        .fallback(ImageResolver.getDrawableResForCategory(spot.category))
                        .build(),
                    contentDescription = spot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Distance pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${spot.distance} • ${spot.walkMinutes} min walk",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Secondary
                        )
                    }
                }

                // Photo attribution badge
                val photoAttribution = ImageResolver.getAttribution(spot.title, spot.imageUrl, spot.category)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
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

            // Diagnostic Telemetry & Re-Fetch Visualizer
            com.example.ui.components.ImageDiagnosticVisualizerCard(
                spot = spot,
                onReFetchImage = onReFetchImage
            )

            // Community Photos (Upload & Moderation Report)
            Surface(
                color = SurfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMMUNITY PHOTOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = BrandTeal
                        )

                        if (photosErrorMessage != null) {
                            Text(
                                text = photosErrorMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandCoral,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (onUploadPhoto != null) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                enabled = !isUploadingPhoto,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("upload_spot_photo_btn")
                            ) {
                                if (isUploadingPhoto) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (visibleSpotPhotos.isEmpty()) {
                        Text(
                            text = if (onUploadPhoto != null) {
                                "No community photos yet. Share the first one!"
                            } else {
                                "No community photos yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        visibleSpotPhotos.forEach { photo ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(photo.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Community photo for ${spot.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (onReportPhoto != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.55f))
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    reportStatusMessage = null
                                                    val reportResult = onReportPhoto.invoke(photo.id)
                                                    reportStatusMessage = if (reportResult.isSuccess) {
                                                        "Report submitted. Thank you for keeping VividScout safe!"
                                                    } else {
                                                        reportResult.exceptionOrNull()?.message
                                                            ?: "Report failed. Please try again."
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("report_photo_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Flag,
                                                contentDescription = "Report this photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uploadStatusMessage != null) {
                        Text(
                            text = uploadStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Secondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (reportStatusMessage != null) {
                        Text(
                            text = reportStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandCoral,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title and Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spot.badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Secondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = spot.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (spot.rating != null || spot.reviewCount != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            if (spot.rating != null) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = PrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            val ratingReviewText = when {
                                spot.rating != null && spot.reviewCount != null -> "${spot.rating} (${spot.reviewCount})"
                                spot.rating != null -> "${spot.rating}"
                                else -> ""
                            }
                            if (ratingReviewText.isNotEmpty()) {
                                Text(
                                    text = ratingReviewText,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Opening Hours & Best Season Tags Row
                val openingStatus = remember(spot.openingHours) {
                    OpeningHoursParser.parseOpeningStatus(spot.openingHours)
                }
                val bestSeason = spot.bestSeason

                if (openingStatus != null || !bestSeason.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (openingStatus != null) {
                            val (statusText, statusBg, statusFg) = when (openingStatus) {
                                OpeningStatus.OPEN_NOW -> Triple("Open now", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF059669))
                                OpeningStatus.CLOSED_NOW -> Triple("Closed now", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFDC2626))
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = statusBg,
                                modifier = Modifier.testTag("spot_detail_opening_status_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusFg)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = statusFg,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (!bestSeason.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = BrandTeal.copy(alpha = 0.15f),
                                modifier = Modifier.testTag("spot_detail_best_season_badge")
                            ) {
                                Text(
                                    text = "✨ Best: $bestSeason",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandTeal,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Live Community Spot Status Alert (e.g. "Bakery out of bread")
                if (latestStatus != null) {
                    val decayTier = TipDecayUtils.calculateDecayTier(latestStatus.timestamp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = decayTier.containerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, decayTier.dotColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spot_detail_live_status_alert")
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                var selectedDetailTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: History

                // Tab Switcher: Overview vs History
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceContainerLow)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selectedDetailTab == 0) SurfaceContainerLowest else Color.Transparent)
                            .clickable { selectedDetailTab = 0 }
                            .padding(vertical = 8.dp)
                            .testTag("spot_detail_tab_overview"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedDetailTab == 0) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selectedDetailTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selectedDetailTab == 1) SurfaceContainerLowest else Color.Transparent)
                            .clickable { selectedDetailTab = 1 }
                            .padding(vertical = 8.dp)
                            .testTag("spot_detail_tab_history"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "History & Knowledge",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedDetailTab == 1) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selectedDetailTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedDetailTab == 0) {
                    // Description Overview
                    Text(
                        text = spot.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                } else {
                    // History & Knowledge Tab Content
                    val currentLangCode = LocalAppLanguage.current.code
                    val hasWikipediaExtract = spot.description.length > 80 && !spot.description.contains("cataloged on Wikidata", ignoreCase = true)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hasWikipediaExtract) {
                            Text(
                                text = "WIKIPEDIA HISTORICAL EXTRACT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = BrandTeal
                            )
                            Text(
                                text = spot.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                            SourceAttributionRow(
                                sourceNameOrTag = "Wikipedia (${currentLangCode.uppercase()})",
                                license = "CC BY-SA 4.0",
                                sourceUrl = "https://en.wikipedia.org/wiki/${spot.title.replace(" ", "_")}"
                            )
                        } else {
                            // Fallback when no Wikipedia article exists: short message + Wikidata description
                            Text(
                                text = "WIKIDATA KNOWLEDGE GRAPH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = Secondary
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Detailed Wikipedia article unavailable for this landmark.",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = spot.description.ifBlank { "Historic & cultural landmark cataloged on Wikidata Knowledge Graph." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            SourceAttributionRow(
                                sourceNameOrTag = "Wikidata Knowledge Graph",
                                license = "CC0 1.0 Universal",
                                sourceUrl = "https://www.wikidata.org"
                            )
                        }
                    }
                }

                // Wikidata Historical Context Card (if spot has Wikidata tags/status)
                val isWikidataSpot = spot.tagsRaw.contains("wikidata", ignoreCase = true) ||
                        spot.statusBadge.contains("Wikidata", ignoreCase = true) ||
                        spot.badgeLabel.contains("Wikidata", ignoreCase = true)

                if (isWikidataSpot) {
                    val tagParts = spot.tagsRaw.split("|||")
                    val qid = tagParts.getOrNull(1)?.takeIf { it.startsWith("Q") } ?: "Knowledge Graph"
                    val commonsMediaId = tagParts.find { it.startsWith("File:") || it.startsWith("Image:") } ?: ""

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wikidata_historical_context_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Historical Context",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "QID: $qid",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = spot.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )

                            if (commonsMediaId.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Wikimedia Commons: $commonsMediaId",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Weather & Visit Suitability Forecast Card
                WeatherCard(
                    weather = weatherReport,
                    isLoading = isWeatherLoading,
                    onRefreshClick = { fetchWeather(forceRefresh = true) }
                )

                // Audio Lore Player (if available)
                if (spot.audioDuration != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3E8FF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleAudio() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Tertiary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPlayingAudio) "${strings.listenQuickLore}..." else "${strings.listenQuickLore} (${spot.audioDuration})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Tertiary
                                )
                                Text(
                                    text = "Narrated by local scout Lisbon historian",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Pro-Tip
                if (spot.proTip != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Wanderer Pro-Tip 💡",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = spot.proTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Data Source Attribution Row
                SourceAttributionRow(
                    sourceNameOrTag = spot.contributor ?: spot.badgeLabel,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Community Ground Truth & Tips Section
                Surface(
                    color = SurfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spot_detail_community_section")
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Button(
                                onClick = { isPostTipOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("spot_detail_post_tip_btn")
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tipsForSpot.take(3).forEach { tip ->
                                    CommunityTipCard(
                                        tip = tip,
                                        onUpvoteClick = { tipId ->
                                            scope.launch {
                                                db.communityTipDao().incrementUpvote(tipId)
                                            }
                                        },
                                        showSpotHeader = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Offline Saving & Discovered Status Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = Secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.offlineNotes,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Discovered Toggle Pill Button
                            Button(
                                onClick = onToggleDiscovered,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (spot.isDiscovered) Secondary else SecondaryContainer.copy(alpha = 0.6f),
                                    contentColor = if (spot.isDiscovered) Color.White else Secondary
                                ),
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("bottomsheet_toggle_discovered_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (spot.isDiscovered) strings.markDiscoveredDone else strings.markAsDiscovered,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = noteText,
                            onValueChange = {
                                noteText = it
                                isNoteSaved = false
                            },
                            placeholder = {
                                Text(
                                    text = strings.addOfflineNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spot_offline_note_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerLowest,
                                unfocusedContainerColor = SurfaceContainerLowest,
                                focusedBorderColor = Secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            maxLines = 3
                        )

                        if (noteText != (spot.userNotes ?: "") || isNoteSaved) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isNoteSaved) {
                                    Text(
                                        text = "Saved to Room ✓",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Secondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Button(
                                    onClick = {
                                        onSaveNote(noteText)
                                        isNoteSaved = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Secondary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp).testTag("save_spot_note_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = strings.saveNote,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save bookmark
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (spot.isSaved) PrimaryContainer.copy(alpha = 0.15f) else SurfaceContainerLow)
                    ) {
                        Icon(
                            imageVector = if (spot.isSaved) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Save",
                            tint = if (spot.isSaved) PrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Upvote
                    Button(
                        onClick = onUpvote,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Upvote",
                            modifier = Modifier.size(16.dp)
                        )
                        if (spot.upvotes != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${spot.upvotes}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Start Navigation CTA
                    Button(
                        onClick = {
                            onDismiss()
                            onPlanRoute?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("spot_detail_directions_cta")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.directions,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    PostTipDialog(
        isOpen = isPostTipOpen,
        onDismiss = { isPostTipOpen = false },
        onSubmit = { spotId, spotTitle, content, isStatus, category ->
            if (onPostTip != null) {
                onPostTip(spotId, spotTitle, content, isStatus, category)
            } else {
                val appContainer = (context.applicationContext as? VividScoutApplication)?.container
                if (!isAppContainerUnavailable(appContainer)) {
                    val repo = appContainer?.communityTipRepository
                    scope.launch {
                        repo?.postTip(spotId, spotTitle, content, isStatus, category)
                    }
                } else {
                    Toast.makeText(
                        context,
                        strings.postTipErrorContainerUnavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        initialSpot = spot
    )
}

/**
 * Evaluates whether the application DI container or community tip repository is unavailable for fallback tip posting.
 */
fun isAppContainerUnavailable(appContainer: AppContainer?): Boolean {
    return appContainer?.communityTipRepository == null
}
