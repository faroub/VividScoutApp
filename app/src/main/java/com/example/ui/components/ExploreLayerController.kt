package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ExploreMapLayer
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandMidnight
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.SurfaceContainerLowest

/**
 * Interactive floating Layer Controller for Explore screen.
 * Expands into a frosted glass panel to toggle Historical, Culinary, and Shortcut overlays.
 */
@Composable
fun ExploreLayerController(
    activeLayers: Set<ExploreMapLayer>,
    spots: List<ScoutSpotEntity>,
    onToggleLayer: (ExploreMapLayer) -> Unit,
    onSetAllLayers: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Count spots matching each layer
    val spotCounts = remember(spots) {
        ExploreMapLayer.entries.associateWith { layer ->
            spots.count { ExploreMapLayer.fromSpotCategory(it.category) == layer }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // Expanded Frosted Glass Overlay Panel
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                scaleOut(targetScale = 0.9f)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceContainerLowest.copy(alpha = 0.96f),
                shadowElevation = 18.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            BrandTeal.copy(alpha = 0.6f),
                            BrandIndigo.copy(alpha = 0.2f)
                        )
                    )
                ),
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 320.dp)
                    .padding(bottom = 12.dp)
                    .testTag("explore_layer_panel")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Header: Title & Quick All/Clear Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Map Overlays",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${activeLayers.size} of 3 active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row {
                            TextButton(
                                onClick = { onSetAllLayers(true) },
                                modifier = Modifier.testTag("layer_toggle_all")
                            ) {
                                Text(
                                    text = "All",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandTeal
                                )
                            }
                            TextButton(
                                onClick = { onSetAllLayers(false) },
                                modifier = Modifier.testTag("layer_toggle_clear")
                            ) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Historical Layer Toggle Row
                    LayerToggleRow(
                        layer = ExploreMapLayer.HISTORICAL,
                        icon = Icons.Default.AccountBalance,
                        isActive = activeLayers.contains(ExploreMapLayer.HISTORICAL),
                        spotCount = spotCounts[ExploreMapLayer.HISTORICAL] ?: 0,
                        onToggle = { onToggleLayer(ExploreMapLayer.HISTORICAL) },
                        testTag = "layer_toggle_historical"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Culinary Layer Toggle Row
                    LayerToggleRow(
                        layer = ExploreMapLayer.CULINARY,
                        icon = Icons.Default.Restaurant,
                        isActive = activeLayers.contains(ExploreMapLayer.CULINARY),
                        spotCount = spotCounts[ExploreMapLayer.CULINARY] ?: 0,
                        onToggle = { onToggleLayer(ExploreMapLayer.CULINARY) },
                        testTag = "layer_toggle_culinary"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Shortcut Layer Toggle Row
                    LayerToggleRow(
                        layer = ExploreMapLayer.SHORTCUT,
                        icon = Icons.Default.AltRoute,
                        isActive = activeLayers.contains(ExploreMapLayer.SHORTCUT),
                        spotCount = spotCounts[ExploreMapLayer.SHORTCUT] ?: 0,
                        onToggle = { onToggleLayer(ExploreMapLayer.SHORTCUT) },
                        testTag = "layer_toggle_shortcut"
                    )
                }
            }
        }

        // Floating Trigger Button
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(999.dp),
            color = BrandNight,
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (activeLayers.isNotEmpty()) BrandTeal.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(999.dp))
                .testTag("explore_layer_controller_toggle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Map Layers",
                    tint = if (activeLayers.isNotEmpty()) BrandTeal else Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Layers",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Active layer count indicator badge
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeLayers.isNotEmpty()) BrandTeal else Color.White.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeLayers.size.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        ),
                        color = if (activeLayers.isNotEmpty()) BrandMidnight else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerToggleRow(
    layer: ExploreMapLayer,
    icon: ImageVector,
    isActive: Boolean,
    spotCount: Int,
    onToggle: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) layer.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) layer.color.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Themed Icon Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) layer.color.copy(alpha = 0.22f) else Color.Gray.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) layer.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = layer.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(layer.color.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$spotCount spots",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = layer.color
                            )
                        }
                    }
                    Text(
                        text = layer.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = isActive,
                onCheckedChange = { onToggle() },
                thumbContent = if (isActive) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                            tint = Color.White
                        )
                    }
                } else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = layer.color,
                    checkedTrackColor = layer.color.copy(alpha = 0.35f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
