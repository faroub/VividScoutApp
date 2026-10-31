package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CommunityTipEntity
import com.example.data.local.ScoutSpotEntity
import com.example.data.p2p.DemoModeProvider
import com.example.data.p2p.MeshSyncPhase
import com.example.data.p2p.PeerMeshSyncResult
import com.example.ui.components.CommunityTipCard
import com.example.ui.components.PostTipDialog
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.util.TipDecayTier
import com.example.util.TipDecayUtils

enum class CommunityFilter {
    ALL,
    STATUS_ONLY,
    FRESH_ONLY,
    NEARBY
}

@Composable
fun CommunityFeedScreen(
    tips: List<CommunityTipEntity>,
    spots: List<ScoutSpotEntity>,
    syncPhase: MeshSyncPhase,
    lastSyncResult: PeerMeshSyncResult?,
    onTriggerSync: () -> Unit,
    onUpvoteTip: (String) -> Unit,
    onPostTip: (spotId: String, spotTitle: String, content: String, isStatus: Boolean, category: String) -> Unit,
    onSpotClick: (String) -> Unit,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    successMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var activeFilter by remember { mutableStateOf(CommunityFilter.ALL) }
    var isPostModalOpen by remember { mutableStateOf(false) }

    val filteredTips = remember(tips, activeFilter, spots, userCoordinates) {
        val now = System.currentTimeMillis()
        when (activeFilter) {
            CommunityFilter.ALL -> tips
            CommunityFilter.STATUS_ONLY -> tips.filter { it.isStatusUpdate }
            CommunityFilter.FRESH_ONLY -> tips.filter {
                TipDecayUtils.calculateDecayTier(it.timestamp, now) == TipDecayTier.FRESH
            }
            CommunityFilter.NEARBY -> {
                // Spots within 1500m of user coordinates
                val nearbySpotIds = spots.filter { spot ->
                    val dist = com.example.data.location.LocationTracker.distanceBetween(
                        userCoordinates.first, userCoordinates.second,
                        spot.latitude, spot.longitude
                    )
                    dist <= 1500f
                }.map { it.id }.toSet()
                tips.filter { it.spotId in nearbySpotIds }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("community_feed_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Section
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = strings.communityNotesTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        ),
                        color = PrimaryContainer
                    )
                    Text(
                        text = "Lisbon Scout Notes & Insights",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.communityNotesSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Success Confirmation Banner (if tip was posted)
            if (!successMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = successMessage,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF059669)
                            )
                        }
                    }
                }
            }

            // Peer-to-Peer Mesh Radar & Sync Status Card
            item {
                PeerMeshRadarCard(
                    syncPhase = syncPhase,
                    lastSyncResult = lastSyncResult,
                    onTriggerSync = onTriggerSync
                )
            }

            // Filter Chips Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("community_filter_row")
                ) {
                    item {
                        FilterChip(
                            selected = activeFilter == CommunityFilter.ALL,
                            onClick = { activeFilter = CommunityFilter.ALL },
                            label = { Text("All Updates (${tips.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == CommunityFilter.STATUS_ONLY,
                            onClick = { activeFilter = CommunityFilter.STATUS_ONLY },
                            label = { Text("Live Status (${tips.count { it.isStatusUpdate }})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == CommunityFilter.FRESH_ONLY,
                            onClick = { activeFilter = CommunityFilter.FRESH_ONLY },
                            label = { Text("Fresh <2h") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == CommunityFilter.NEARBY,
                            onClick = { activeFilter = CommunityFilter.NEARBY },
                            label = { Text("Nearby <1.5km") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Feed Items List
            if (filteredTips.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No Community Notes Yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.communityEmptyNotesDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredTips, key = { it.id }) { tip ->
                    CommunityTipCard(
                        tip = tip,
                        onUpvoteClick = onUpvoteTip,
                        onSpotClick = onSpotClick,
                        showSpotHeader = true
                    )
                }
            }
        }

        // Floating Broadcast Action Button
        FloatingActionButton(
            onClick = { isPostModalOpen = true },
            containerColor = PrimaryContainer,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
                .testTag("nav_post_tip_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Post Note")
                Text(
                    text = "Post Note",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Modal Composer Bottom Sheet
        PostTipDialog(
            isOpen = isPostModalOpen,
            onDismiss = { isPostModalOpen = false },
            onSubmit = { spotId, spotTitle, content, isStatus, category ->
                onPostTip(spotId, spotTitle, content, isStatus, category)
            },
            availableSpots = spots
        )
    }
}

@Composable
fun PeerMeshRadarCard(
    syncPhase: MeshSyncPhase,
    lastSyncResult: PeerMeshSyncResult?,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isDemoActive = DemoModeProvider.isDemoPeerSyncEnabled
    val isSyncing = syncPhase == MeshSyncPhase.DISCOVERING_PEERS || syncPhase == MeshSyncPhase.EXCHANGING_GOSSIP

    val infiniteTransition = rememberInfiniteTransition(label = "meshPulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotate"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSyncing) BrandTeal.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth().testTag("peer_mesh_radar_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BrandTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDemoActive) Icons.Default.Radar else Icons.Default.Storage,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier
                                .size(24.dp)
                                .then(if (isSyncing && isDemoActive) Modifier.rotate(rotation) else Modifier)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isDemoActive) strings.communityDemoModeActive else strings.communitySavedLocally.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = BrandTeal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSyncing) Color(0xFF10B981) else Color(0xFF0D9488))
                            )
                        }
                        Text(
                            text = if (isDemoActive) {
                                when (syncPhase) {
                                    MeshSyncPhase.DISCOVERING_PEERS -> "Scanning BLE / Wi-Fi Mesh..."
                                    MeshSyncPhase.EXCHANGING_GOSSIP -> "Exchanging Tip Vectors..."
                                    MeshSyncPhase.SYNC_SUCCESS -> "Mesh Synchronized!"
                                    MeshSyncPhase.IDLE -> strings.communityDemoPeersConnected
                                }
                            } else {
                                strings.communityStorageReady
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Button(
                    onClick = onTriggerSync,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("mesh_sync_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDemoActive) "Sync Demo" else strings.communityRefreshLocal,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Telemetry line: peers & last sync stats
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDemoActive) {
                            "Demo Peers: @clara (18m) • @miguel (34m) • @tiago (55m)"
                        } else {
                            strings.communityFutureSharingNote
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = if (isDemoActive && lastSyncResult != null) {
                            "+${lastSyncResult.tipsReceived} tips"
                        } else {
                            strings.communitySavedLocally
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BrandTeal,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
