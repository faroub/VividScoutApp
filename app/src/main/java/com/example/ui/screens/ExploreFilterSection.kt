package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLowest

@Composable
fun ExploreFilterRibbon(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("explore_filter_ribbon")
    ) {
        item {
            ChiadoFilterChip(
                name = strings.allNearby,
                count = "48",
                icon = Icons.Default.NearMe,
                isSelected = selectedCategory == "all",
                selectedColor = PrimaryContainer,
                onClick = { onCategorySelected("all") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.trendingAroundYou,
                count = "18",
                icon = Icons.Default.LocalFireDepartment,
                isSelected = selectedCategory == "trending",
                selectedColor = Color(0xFFEA580C),
                onClick = { onCategorySelected("trending") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.sights,
                count = "24",
                icon = Icons.Default.AutoAwesome,
                isSelected = selectedCategory == "sights",
                selectedColor = Color(0xFF10B981),
                onClick = { onCategorySelected("sights") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.streetEats,
                count = "15",
                icon = Icons.Default.Restaurant,
                isSelected = selectedCategory == "eats",
                selectedColor = Color(0xFFF59E0B),
                onClick = { onCategorySelected("eats") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.history,
                count = "9",
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "history",
                selectedColor = Color(0xFF7C3AED),
                onClick = { onCategorySelected("history") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.hiddenGems,
                count = "12",
                icon = Icons.Default.NearMe,
                isSelected = selectedCategory == "shortcut",
                selectedColor = Secondary,
                onClick = { onCategorySelected("shortcut") }
            )
        }
    }
}

@Composable
fun ChiadoFilterChip(
    name: String,
    count: String,
    icon: Any,
    isSelected: Boolean,
    selectedColor: Color = PrimaryContainer,
    onClick: () -> Unit
) {
    val bg = if (isSelected) selectedColor else SurfaceContainerLowest
    val fg = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        shadowElevation = if (isSelected) 8.dp else 2.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (icon) {
                is androidx.compose.ui.graphics.vector.ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.25f) else SurfaceContainerHigh)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CommunityScoutCalloutCard(onAddSpotClick: () -> Unit) {
    val strings = LocalAppStrings.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE6F4F1),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = strings.foundSecretCorner,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.earnScoutBadges,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onAddSpotClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceContainerLowest,
                    contentColor = Secondary
                ),
                shape = RoundedCornerShape(999.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("share_spot_callout_button")
            ) {
                Text(
                    text = strings.shareSpot,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
