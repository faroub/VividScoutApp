package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.VividScoutApplication
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutSpotEntity
import com.example.di.AppContainer
import com.example.ui.components.ImageDiagnosticVisualizerCard
import com.example.ui.components.PostTipDialog
import com.example.ui.components.SourceAttributionRow
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import com.example.util.OpeningHoursParser
import com.example.util.OpeningStatus
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
    onUploadPhoto: (suspend (spotId: String, uri: android.net.Uri) -> Result<com.example.data.repository.SpotPhotoData>)? = null,
    onReportPhoto: (suspend (photoId: String) -> Result<Int>)? = null,
    onLoadOlderPhotos: (suspend (spotId: String, cursor: com.example.data.repository.PhotoPageCursor?) -> Result<com.example.data.repository.OlderPhotosPage>)? = null,
    onReportTip: ((tipId: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reportingTipId by remember { mutableStateOf<String?>(null) }

    var isPostTipOpen by remember { mutableStateOf(false) }
    val db = remember { AppDatabase.getDatabase(context) }
    val tipsForSpot by db.communityTipDao().getTipsForSpotFlow(spot.id).collectAsState(initial = emptyList())
    val latestStatus = tipsForSpot.firstOrNull { it.isStatusUpdate }

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
            SpotDetailHeroHeader(
                spot = spot,
                onDismiss = onDismiss
            )

            // Diagnostic Telemetry & Re-Fetch Visualizer
            ImageDiagnosticVisualizerCard(
                spot = spot,
                onReFetchImage = onReFetchImage
            )

            SpotDetailPhotosSection(
                spot = spot,
                onUploadPhoto = onUploadPhoto,
                onReportPhoto = onReportPhoto,
                onLoadOlderPhotos = onLoadOlderPhotos
            )

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
                                color = com.example.ui.theme.BrandTeal.copy(alpha = 0.15f),
                                modifier = Modifier.testTag("spot_detail_best_season_badge")
                            ) {
                                Text(
                                    text = "✨ Best: $bestSeason",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = com.example.ui.theme.BrandTeal,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                if (latestStatus != null) {
                    SpotDetailLiveStatusAlert(latestStatus = latestStatus)
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
                    SpotDetailHistoryTabContent(spot = spot)
                }

                SpotDetailWikidataContextCard(spot = spot)

                SpotDetailWeatherSection(spot = spot)

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

                SpotDetailCommunitySection(
                    spot = spot,
                    tipsForSpot = tipsForSpot,
                    onPostTipClick = { isPostTipOpen = true },
                    onUpvoteClick = { tipId ->
                        scope.launch {
                            db.communityTipDao().incrementUpvote(tipId)
                        }
                    },
                    onReportClick = onReportTip?.let {
                        { tipId -> reportingTipId = tipId }
                    }
                )

                SpotDetailNoteAndActions(
                    spot = spot,
                    onSaveNote = onSaveNote,
                    onToggleDiscovered = onToggleDiscovered,
                    onToggleSave = onToggleSave,
                    onUpvote = onUpvote,
                    onPlanRoute = onPlanRoute,
                    onDismiss = onDismiss
                )
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

    // Report confirmation: a report is a moderation action, so the user confirms
    // before the reportCommunityTip Cloud Function is called.
    reportingTipId?.let { tipId ->
        ReportCommunityTipDialog(
            tipId = tipId,
            onConfirm = { onReportTip?.invoke(tipId) },
            onDismiss = { reportingTipId = null }
        )
    }
}

/**
 * Evaluates whether the application DI container or community tip repository is unavailable for fallback tip posting.
 */
fun isAppContainerUnavailable(appContainer: AppContainer?): Boolean {
    return appContainer?.communityTipRepository == null
}
