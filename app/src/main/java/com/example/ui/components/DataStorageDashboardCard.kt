package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.EatsAmber
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.util.StorageSizeCalculator

/**
 * Expandable settings dashboard displaying database metrics, a segmented storage progress bar,
 * and a manual 'Clear Cache' button to purge non-saved POIs.
 */
@Composable
fun DataStorageDashboardCard(
    totalSpotsCount: Int,
    savedCount: Int,
    discoveredCount: Int,
    draftCount: Int,
    databaseSizeBytes: Long,
    onClearCacheClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "chevron_rotation"
    )

    // Calculate transient count (spots that are neither saved, discovered, nor drafts)
    val permanentCount = (savedCount + discoveredCount).coerceAtMost(totalSpotsCount)
    val transientCount = (totalSpotsCount - permanentCount - draftCount).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_storage_dashboard_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row (Clickable to Expand / Collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp)
                    .testTag("data_storage_expand_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = BrandTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Data Usage & Storage",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val formattedSize = StorageSizeCalculator.formatBytes(databaseSizeBytes)
                        Text(
                            text = "Room DB: $formattedSize • $totalSpotsCount Total POIs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }

            // Expandable Content Section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Segmented Storage Progress Bar
                    SegmentedStorageProgressBar(
                        permanentCount = permanentCount,
                        draftCount = draftCount,
                        transientCount = transientCount,
                        totalCount = totalSpotsCount
                    )

                    // 2. Metrics Legend
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StorageLegendRow(
                            label = "Permanent Saved & Discovered",
                            count = permanentCount,
                            color = Secondary,
                            badge = "Protected"
                        )
                        StorageLegendRow(
                            label = "Pending Offline Drafts",
                            count = draftCount,
                            color = EatsAmber,
                            badge = "Local Draft"
                        )
                        StorageLegendRow(
                            label = "Transient Cached POIs",
                            count = transientCount,
                            color = BrandCoral,
                            badge = "Purgeable"
                        )
                    }

                    // 3. Clear Cache Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Transient Cache",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$transientCount places ready to purge",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = onClearCacheClick,
                            enabled = transientCount > 0,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BrandCoral,
                                disabledContentColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("clear_cache_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (transientCount > 0) "Clear Cache ($transientCount)" else "Cache Empty",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual multi-color segmented progress bar indicating the proportional allocation of database items.
 */
@Composable
fun SegmentedStorageProgressBar(
    permanentCount: Int,
    draftCount: Int,
    transientCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val total = totalCount.coerceAtLeast(1)
    val permanentWeight = (permanentCount.toFloat() / total).coerceIn(0f, 1f)
    val draftWeight = (draftCount.toFloat() / total).coerceIn(0f, 1f)
    val transientWeight = (transientCount.toFloat() / total).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceContainerLow)
        ) {
            if (permanentWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(permanentWeight)
                        .height(10.dp)
                        .background(Secondary)
                )
            }
            if (draftWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(draftWeight)
                        .height(10.dp)
                        .background(EatsAmber)
                )
            }
            if (transientWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(transientWeight)
                        .height(10.dp)
                        .background(BrandCoral)
                )
            }
        }
    }
}

@Composable
private fun StorageLegendRow(
    label: String,
    count: Int,
    color: Color,
    badge: String
) {
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
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
