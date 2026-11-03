package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.ScoutSpotEntity
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.EatsAmber
import com.example.ui.theme.EatsAmberBg
import com.example.ui.theme.HistoryPurple
import com.example.ui.theme.HistoryPurpleBg
import com.example.ui.theme.SightsGreen
import com.example.ui.theme.SightsGreenBg
import com.example.util.Geo
import com.example.util.ImageResolver
import java.util.Locale

/**
 * Interactive Floating Spot Preview Card rendered at the bottom of the map when a pin marker is tapped.
 */
@Composable
fun SpotPreviewPopupCard(
    spot: ScoutSpotEntity,
    onCardClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    userLat: Double = Double.NaN,
    userLng: Double = Double.NaN
) {
    val categoryColor = when (spot.category.lowercase()) {
        "sights" -> SightsGreen
        "eats" -> EatsAmber
        "history" -> HistoryPurple
        "shortcut" -> EatsAmber
        else -> BrandTeal
    }
    val categoryBg = when (spot.category.lowercase()) {
        "sights" -> SightsGreenBg
        "eats" -> EatsAmberBg
        "history" -> HistoryPurpleBg
        else -> BrandTeal.copy(alpha = 0.15f)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20F172A),
        border = BorderStroke(1.5.dp, categoryColor.copy(alpha = 0.65f)),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("spot_preview_popup_card")
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spot Thumbnail Image
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                val imageModel = ImageResolver.resolveImageModel(spot.imageUrl, spot.category, spot.title, spot.id)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = spot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (spot.isSaved) {
                    Surface(
                        shape = CircleShape,
                        color = BrandCoral,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Saved",
                            tint = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Spot Information Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Category Tag & Walking Distance / Minutes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = categoryBg,
                        modifier = Modifier.testTag("preview_category_badge")
                    ) {
                        Text(
                            text = spot.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    val formattedDist = Geo.formatDistanceAndWalkTime(
                        userLat, userLng, spot.latitude, spot.longitude
                    )
                    val walkText = if (formattedDist != "—") {
                        formattedDist
                    } else if (spot.distance.isNotBlank()) {
                        "${spot.distance}${if (spot.walkMinutes > 0) " • ${spot.walkMinutes} min walk" else ""}"
                    } else {
                        "—"
                    }

                    Text(
                        text = walkText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Spot Name
                Text(
                    text = spot.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("preview_spot_title")
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Rating & Snippet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val spotRating = spot.rating ?: 0.0
                    if (spotRating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f", spotRating),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFFFBBF24)
                            )
                        }
                    }

                    Text(
                        text = spot.description.ifBlank { "Tap to explore history & tips" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action / Dismiss Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(68.dp)
            ) {
                // Dismiss Icon Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("dismiss_preview_popup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Forward "View Details" Pill Button
                Surface(
                    shape = CircleShape,
                    color = categoryColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.5f)),
                    onClick = onCardClick,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("preview_open_details_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Details",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
