package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal

/**
 * Real-time Offline Database Sync Status Data Model.
 */
data class OfflineSyncStatus(
    val totalLocalGems: Int = 0,
    val pendingDraftsCount: Int = 0,
    val lastOsmSyncedCount: Int = 0,
    val isSyncing: Boolean = false
)

/**
 * Floating Visual Status Badge displaying offline Room DB metrics:
 * - Total local gems stored in Room
 * - Queued offline drafts pending upload
 * - Count of newly discovered nodes from the latest Overpass sync
 */
@Composable
fun OfflineSyncStatusBadge(
    status: OfflineSyncStatus,
    onClick: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        status.isSyncing -> BrandTeal
        status.pendingDraftsCount > 0 -> BrandCoral
        else -> BrandTeal.copy(alpha = 0.5f)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xF2080B1E),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp,
        modifier = modifier
            .clickable { onClick() }
            .testTag("offline_sync_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (status.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = BrandTeal
                )
            } else {
                // Status dot indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (status.pendingDraftsCount > 0) BrandCoral else Color(0xFF10B981)
                        )
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Text summary: 💾 24 Local • 📝 2 Queued • 🌐 +12 OSM
            Text(
                text = "💾 ${status.totalLocalGems} Local",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                fontSize = 11.sp
            )

            if (status.pendingDraftsCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• 📝 ${status.pendingDraftsCount} Queued",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = BrandCoral,
                    fontSize = 11.sp
                )
            }

            if (status.lastOsmSyncedCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• 🌐 +${status.lastOsmSyncedCount} OSM",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = BrandTeal,
                    fontSize = 11.sp
                )
            }

            if (onOpenDiagnostics != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(BrandTeal.copy(alpha = 0.25f))
                        .clickable { onOpenDiagnostics() }
                        .testTag("sync_badge_diagnostic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Open Overpass Diagnostics",
                        tint = BrandTeal,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
