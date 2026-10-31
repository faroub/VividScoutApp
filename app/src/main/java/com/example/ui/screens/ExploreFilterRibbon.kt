package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.Tertiary
import com.example.ui.theme.TertiaryFixed

@Composable
fun CategoryFilterRibbon(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // All Nearby
        item {
            FilterRibbonPill(
                title = strings.allNearby,
                icon = Icons.Default.NearMe,
                isSelected = selectedCategory == "all",
                accentColor = PrimaryContainer,
                onClick = { onCategorySelected("all") }
            )
        }
        // Sights
        item {
            FilterRibbonPill(
                title = strings.sights,
                icon = Icons.Default.PhotoCamera,
                count = "14",
                isSelected = selectedCategory == "sights",
                accentColor = SecondaryContainer,
                textColor = Secondary,
                onClick = { onCategorySelected("sights") }
            )
        }
        // Warm Bakes
        item {
            FilterRibbonPill(
                title = strings.warmBakes,
                icon = Icons.Default.Restaurant,
                showPulse = true,
                isSelected = selectedCategory == "eats",
                accentColor = PrimaryFixed,
                textColor = Primary,
                onClick = { onCategorySelected("eats") }
            )
        }
        // Gothic & History
        item {
            FilterRibbonPill(
                title = strings.gothicHistory,
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "history",
                accentColor = TertiaryFixed,
                textColor = Tertiary,
                onClick = { onCategorySelected("history") }
            )
        }
        // Secret Spots / Shortcuts
        item {
            FilterRibbonPill(
                title = strings.secretSpots,
                icon = Icons.Default.Verified,
                isSelected = selectedCategory == "shortcut",
                accentColor = SurfaceContainerHigh,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onCategorySelected("shortcut") }
            )
        }
    }
}

@Composable
fun FilterRibbonPill(
    title: String,
    icon: Any,
    count: String? = null,
    showPulse: Boolean = false,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    val bg = if (isSelected) PrimaryContainer else accentColor
    val fg = if (isSelected) Color.White else textColor

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        shadowElevation = if (isSelected) 6.dp else 1.dp,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("filter_pill_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (icon) {
                is ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = fg
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg
                    )
                }
            }
            if (showPulse && !isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Primary)
                )
            }
        }
    }
}

@Composable
fun ChiadoCategoryFilterRibbon(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            ChiadoFilterChip(
                name = strings.allSights,
                count = "82",
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "all",
                onClick = { onCategorySelected("all") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.history,
                count = "24",
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "history",
                selectedColor = Tertiary,
                onClick = { onCategorySelected("history") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.streetEats,
                count = "18",
                icon = Icons.Default.Restaurant,
                isSelected = selectedCategory == "eats",
                selectedColor = PrimaryContainer,
                onClick = { onCategorySelected("eats") }
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
