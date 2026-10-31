package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CommunityTipEntity
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.util.TipDecayTier
import com.example.util.TipDecayUtils

@Composable
fun CommunityTipCard(
    tip: CommunityTipEntity,
    onUpvoteClick: (String) -> Unit,
    onSpotClick: ((String) -> Unit)? = null,
    showSpotHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val decayTier = TipDecayUtils.calculateDecayTier(tip.timestamp)
    val decayBadgeText = TipDecayUtils.getDecayBadgeText(tip.timestamp)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("community_tip_card_${tip.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Author + Time-Decay Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Author Avatar Initial Circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (tip.isLocallyAuthored) PrimaryContainer else BrandTeal.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tip.authorName.removePrefix("@").take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (tip.isLocallyAuthored) Color.White else BrandTeal
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tip.authorName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (tip.isLocallyAuthored) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• You (${strings.communitySavedLocally})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryContainer
                                )
                            }
                            if (tip.isSampleContent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("sample_content_badge_${tip.id}")
                                ) {
                                    Text(
                                        text = strings.communitySampleTipBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Time-Decay Vitality Badge (Fresh / Active / Aging / Expired)
                TimeDecayBadge(decayTier = decayTier, text = decayBadgeText)
            }

            // Spot Title Jump Header (if in city-wide feed)
            if (showSpotHeader && tip.spotTitle.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLow)
                        .clickable(enabled = onSpotClick != null) { onSpotClick?.invoke(tip.spotId) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = PrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tip.spotTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    if (onSpotClick != null) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Gem",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Status Category Chip (if this is an operational status update)
            if (tip.isStatusUpdate) {
                StatusCategoryChip(category = tip.statusCategory)
            }

            // Tip Content Body
            Text(
                text = tip.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            // Footer: P2P Node tag + Upvote Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "via ${tip.peerNodeId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )

                // Upvote Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PrimaryContainer.copy(alpha = 0.08f))
                        .clickable { onUpvoteClick(tip.id) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("upvote_tip_${tip.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "Upvote",
                        tint = PrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    if (tip.upvotes > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${tip.upvotes}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeDecayBadge(
    decayTier: TipDecayTier,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = decayTier.containerColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(decayTier.dotColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = decayTier.contentColor
            )
        }
    }
}

@Composable
fun StatusCategoryChip(category: String, modifier: Modifier = Modifier) {
    val style = when (category) {
        "STOCK_SHORTAGE" -> StatusChipStyle("STOCK ALERT", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFDC2626), Icons.Default.WarningAmber)
        "LINE_QUEUE" -> StatusChipStyle("QUEUE UPDATE", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), Icons.Default.HourglassTop)
        "FRESH_BATCH" -> StatusChipStyle("WARM BATCH OUT", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF059669), Icons.Default.AutoAwesome)
        "ATMOSPHERE" -> StatusChipStyle("LIVE ATMOSPHERE", Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF7C3AED), Icons.Default.AutoAwesome)
        else -> StatusChipStyle("SPOT STATUS", BrandTeal.copy(alpha = 0.15f), BrandTeal, Icons.Default.AutoAwesome)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = style.bgColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    fontSize = 9.sp
                ),
                color = style.textColor
            )
        }
    }
}

private data class StatusChipStyle(
    val label: String,
    val bgColor: Color,
    val textColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
