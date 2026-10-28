package com.example.ui.dialogs

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
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
import com.example.data.local.ScoutSpotEntity
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary

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
    onPlanRoute: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strings = LocalAppStrings.current
    var noteText by remember(spot.id) { mutableStateOf(spot.userNotes ?: "") }
    var isNoteSaved by remember { mutableStateOf(false) }

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
                    model = spot.imageUrl,
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = PrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${spot.rating} (${spot.reviewCount})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Description
                Text(
                    text = spot.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
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
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${spot.upvotes}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
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
}
