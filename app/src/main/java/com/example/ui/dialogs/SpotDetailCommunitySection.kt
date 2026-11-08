package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CommunityTipEntity
import com.example.data.local.ScoutSpotEntity
import com.example.ui.components.CommunityTipCard
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.SurfaceContainerLow
import com.example.util.TipDecayUtils

@Composable
fun SpotDetailLiveStatusAlert(latestStatus: CommunityTipEntity) {
    // Live Community Spot Status Alert (e.g. "Bakery out of bread")
    val decayTier = TipDecayUtils.calculateDecayTier(latestStatus.timestamp)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = decayTier.containerColor,
        border = BorderStroke(1.dp, decayTier.dotColor.copy(alpha = 0.5f)),
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

@Composable
fun SpotDetailCommunitySection(
    spot: ScoutSpotEntity,
    tipsForSpot: List<CommunityTipEntity>,
    onPostTipClick: () -> Unit,
    onUpvoteClick: (tipId: String) -> Unit
) {
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
                    onClick = onPostTipClick,
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
                            onUpvoteClick = onUpvoteClick,
                            showSpotHeader = false
                        )
                    }
                }
            }
        }
    }
}
