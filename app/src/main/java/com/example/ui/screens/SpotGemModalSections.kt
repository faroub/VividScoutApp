package com.example.ui.screens

import android.location.Location
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SourceAttributionRow
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SurfaceContainerLow

@Composable
fun OfflineAlertBanner(
    draftCount: Int,
    onQueueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    val infiniteTransition = rememberInfiniteTransition(label = "ping")
    val pingScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pingScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_alert_banner"),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF59E0B),
                            Color(0xFFF97316),
                            PrimaryContainer
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Icon + Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = strings.offlineAlertTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color.White
                                )
                            }
                            Text(
                                text = strings.offlineAlertSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onDismissClick() }
                            .testTag("dismiss_offline_banner_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Last cached time + Queue draft button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(pingScale)
                                .clip(CircleShape)
                                .background(SecondaryFixed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.lastCached,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .clickable { onQueueClick() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .testTag("queue_status_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = PrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${strings.queueDrafts} ($draftCount)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal header: Community Scout badge, title, subtitle, data-source attribution
 * row, and the close button. Extracted from [SpotGemModal] with no behavior change.
 */
@Composable
internal fun SpotGemModalHeader(
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SecondaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "COMMUNITY SCOUT SUBMISSION",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 9.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.spotAHiddenGem,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = strings.spotGemSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SourceAttributionRow(
                sourceNameOrTag = "OpenStreetMap / Community",
                license = "ODbL",
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceContainerLow)
                .testTag("close_gem_modal_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Spot-name input and the 2x2 category selection grid. Extracted from [SpotGemModal]
 * with no behavior change; the selected category is lifted state.
 */
@Composable
internal fun SpotGemNameAndCategoryFields(
    spotName: String,
    onSpotNameChange: (String) -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    // Spot Name Input Field
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.spotNameLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = strings.offlineReady,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Secondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = spotName,
            onValueChange = onSpotNameChange,
            placeholder = { Text("e.g. Hidden Terrace or Tile Wall") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PinDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gem_spot_name_input"),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainerLow,
                unfocusedContainerColor = SurfaceContainerLow,
                focusedBorderColor = Secondary,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )
    }

    // Select Category (2x2 Grid)
    Column {
        Text(
            text = strings.selectCategory,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryModalChip(
                title = strings.sights,
                subtitle = "Viewpoint, Plaza",
                icon = Icons.Default.Visibility,
                selected = selectedCategory == "sights",
                selectedColor = Color(0xFF059669),
                iconBgColor = Color(0xFFD1FAE5),
                iconTint = Color(0xFF059669),
                onClick = { onSelectCategory("sights") },
                modifier = Modifier.weight(1f)
            )
            CategoryModalChip(
                title = strings.streetEats,
                subtitle = "Kiosks & Bites",
                icon = Icons.Default.RamenDining,
                selected = selectedCategory == "eats",
                selectedColor = Color(0xFFD97706),
                iconBgColor = Color(0xFFFEF3C7),
                iconTint = Color(0xFFD97706),
                onClick = { onSelectCategory("eats") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryModalChip(
                title = strings.history,
                subtitle = "Ruins, Old Fado",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                selected = selectedCategory == "history",
                selectedColor = Color(0xFF7C3AED),
                iconBgColor = Color(0xFFF3E8FF),
                iconTint = Color(0xFF7C3AED),
                onClick = { onSelectCategory("history") },
                modifier = Modifier.weight(1f)
            )
            CategoryModalChip(
                title = strings.shortcut,
                subtitle = "Stairway, Pass",
                icon = Icons.Default.Route,
                selected = selectedCategory == "shortcut",
                selectedColor = Secondary,
                iconBgColor = Color(0xFFCCFBF1),
                iconTint = Secondary,
                onClick = { onSelectCategory("shortcut") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Photo verification dropzone and the GPS-location capture card (or the prominent
 * fallback-location indicator). Extracted from [SpotGemModal] with no behavior change.
 */
@Composable
internal fun SpotGemPhotoAndLocationSection(
    photoSelected: Boolean,
    onPickPhoto: () -> Unit,
    isUsingGpsLocation: Boolean,
    userLocation: Location?,
    userCoordinates: Pair<Double, Double>,
    selectedGlobalCity: String?
) {
    val strings = LocalAppStrings.current
    // Photo Verification Dropzone
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.photoVerification,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PrimaryContainer.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = strings.scoutPointsBonus,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PrimaryContainer.copy(alpha = 0.08f))
                .border(1.5.dp, PrimaryContainer, RoundedCornerShape(18.dp))
                .clickable { onPickPhoto() }
                .padding(16.dp)
                .testTag("photo_verification_dropzone"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(8.dp, CircleShape, spotColor = PrimaryContainer)
                        .clip(CircleShape)
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (photoSelected) strings.photoAttached else strings.tapToSnap,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (photoSelected) Secondary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Supports JPG, PNG up to 10MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = strings.storedLocallyNote,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // GPS Location Captured Card (or prominent Fallback Location indicator)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (!isUsingGpsLocation) Color(0xFFFEF3C7) else SecondaryContainer.copy(alpha = 0.35f),
        border = if (!isUsingGpsLocation) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (!isUsingGpsLocation) Icons.Default.PinDrop else Icons.Default.SatelliteAlt,
                contentDescription = null,
                tint = if (!isUsingGpsLocation) Color(0xFFD97706) else Secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (!isUsingGpsLocation) {
                            "📍 Tagging Fallback Location (${selectedGlobalCity ?: "Selected City"})"
                        } else {
                            strings.locationCaptured
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (!isUsingGpsLocation) Color(0xFF92400E) else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (!isUsingGpsLocation) Color(0xFFD97706) else Secondary)
                    )
                }
                val gpsLabel = if (!isUsingGpsLocation) {
                    String.format(
                        Locale.US,
                        "Simulated coordinates: %.4f° N, %.4f° W",
                        userCoordinates.first,
                        userCoordinates.second
                    )
                } else if (userLocation != null) {
                    String.format(
                        Locale.US,
                        "GPS live: %.4f° N, %.4f° W (±%dm)",
                        userCoordinates.first,
                        userCoordinates.second,
                        userLocation.accuracy.toInt()
                    )
                } else {
                    strings.gpsCachedPrecision
                }
                Text(
                    text = gpsLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!isUsingGpsLocation) Color(0xFFB45309) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Optional offline-notes field, the save-offline/submit action button with its
 * security note, and the offline auto-sync protocol card. Extracted from
 * [SpotGemModal] with no behavior change; submission state stays lifted in the
 * caller, which owns the success-flag transition.
 */
@Composable
internal fun SpotGemNotesAndSubmitSection(
    spotNotes: String,
    onSpotNotesChange: (String) -> Unit,
    isSubmittedSuccess: Boolean,
    onSubmitClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    // Optional Offline Notes Field
    OutlinedTextField(
        value = spotNotes,
        onValueChange = onSpotNotesChange,
        label = { Text(strings.offlineNotes) },
        placeholder = { Text(strings.addOfflineNotes) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gem_field_notes_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceContainerLow,
            unfocusedContainerColor = SurfaceContainerLow,
            focusedBorderColor = Secondary,
            unfocusedBorderColor = Color.Transparent
        ),
        maxLines = 3
    )

    // Action Button: Save Offline & Submit
    Button(
        onClick = onSubmitClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(999.dp),
                spotColor = PrimaryContainer.copy(alpha = 0.45f)
            )
            .testTag("submit_gem_offline_button"),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSubmittedSuccess) Secondary else PrimaryContainer,
            contentColor = Color.White
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSubmittedSuccess) Icons.Default.CheckCircle else Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSubmittedSuccess) strings.queuedInStorage else strings.saveOfflineSubmit,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }
    }

    // Security note
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.OfflinePin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = strings.draftSecurelySaved,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Offline Auto-Sync Protocol Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapCalls,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = strings.autoSyncProtocol,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.autoSyncDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun CategoryModalChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    iconBgColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg = if (selected) selectedColor else SurfaceContainerLow
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor = if (selected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg)
            .clickable { onClick() }
            .padding(10.dp)
            .testTag("category_chip_${title.lowercase()}"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.25f) else iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = textColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor,
                    maxLines = 1
                )
            }
        }
    }
}
