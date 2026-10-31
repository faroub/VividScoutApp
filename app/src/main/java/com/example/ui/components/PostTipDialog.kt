package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScoutSpotEntity
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostTipDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (spotId: String, spotTitle: String, content: String, isStatus: Boolean, category: String) -> Unit,
    initialSpot: ScoutSpotEntity? = null,
    availableSpots: List<ScoutSpotEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSpot by remember(initialSpot) { mutableStateOf(initialSpot ?: availableSpots.firstOrNull()) }
    var isStatusUpdate by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("STOCK_SHORTAGE") }
    var tipText by remember { mutableStateOf("") }

    val presetTemplates = listOf(
        Pair("Bakery out of bread / stock", "STOCK_SHORTAGE"),
        Pair("Fresh warm batch pulled now!", "FRESH_BATCH"),
        Pair("Heavy queue / long wait (>20m)", "LINE_QUEUE"),
        Pair("No wait right now / zero line", "LINE_QUEUE"),
        Pair("Quiet atmosphere & shaded seating", "ATMOSPHERE"),
        Pair("Secret courtyard open today", "INSIGHT")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("post_tip_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.communityNotesTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = PrimaryContainer
                    )
                    Text(
                        text = if (isStatusUpdate) "Record Spot Status" else "Save Wanderer Tip",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Target Gem Display / Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Target Lisbon Gem",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedSpot?.title ?: "Select a gem",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            // Mode Selector: Status Update vs General Tip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isStatusUpdate,
                    onClick = { isStatusUpdate = true },
                    label = { Text("Spot Status Alert") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = !isStatusUpdate,
                    onClick = { isStatusUpdate = false },
                    label = { Text("General Tip") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Preset Status Chips (if in status mode)
            if (isStatusUpdate) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetTemplates) { (preset, category) ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (tipText == preset) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLow,
                                border = if (tipText == preset) androidx.compose.foundation.BorderStroke(1.dp, PrimaryContainer) else null,
                                modifier = Modifier.clickable {
                                    tipText = preset
                                    selectedCategory = category
                                }
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (tipText == preset) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (tipText == preset) PrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Text Input Field
            OutlinedTextField(
                value = tipText,
                onValueChange = { if (it.length <= 140) tipText = it },
                label = { Text(if (isStatusUpdate) "Status details (e.g., 'Bakery out of bread')" else "Your insider recommendation") },
                placeholder = { Text("Short, timely update or recommendation...") },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.communityFutureSharingNote)
                        Text("${tipText.length}/140")
                    }
                },
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_tip_text_field")
            )

            // Submit Button
            Button(
                onClick = {
                    val spot = selectedSpot
                    if (spot != null && tipText.isNotBlank()) {
                        onSubmit(
                            spot.id,
                            spot.title,
                            tipText.trim(),
                            isStatusUpdate,
                            selectedCategory
                        )
                        onDismiss()
                    }
                },
                enabled = selectedSpot != null && tipText.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_tip_button")
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.communitySaveLocallyAction,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
