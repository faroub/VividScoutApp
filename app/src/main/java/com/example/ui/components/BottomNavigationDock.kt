package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.viewmodel.MainTab

@Composable
fun BottomNavigationDock(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onAddSpotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = com.example.ui.theme.LocalAppStrings.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(999.dp),
                    spotColor = Color(0x2E111827),
                    ambientColor = Color(0x1F111827)
                ),
            shape = RoundedCornerShape(999.dp),
            color = SurfaceContainerLowest.copy(alpha = 0.96f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Explore
                NavTabItem(
                    title = strings.explore,
                    selected = currentTab == MainTab.EXPLORE,
                    selectedIcon = Icons.Filled.Explore,
                    unselectedIcon = Icons.Outlined.Explore,
                    testTag = "nav_tab_explore",
                    onClick = { onTabSelected(MainTab.EXPLORE) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 2: Radar
                NavTabItem(
                    title = strings.radar,
                    selected = currentTab == MainTab.RADAR,
                    selectedIcon = Icons.Filled.Radar,
                    unselectedIcon = Icons.Outlined.Radar,
                    showDot = true,
                    testTag = "nav_tab_radar",
                    onClick = { onTabSelected(MainTab.RADAR) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 3: Center Primary '+' FAB
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(50.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            spotColor = PrimaryContainer.copy(alpha = 0.5f)
                        )
                        .clip(CircleShape)
                        .background(PrimaryContainer)
                        .clickable { onAddSpotClick() }
                        .testTag("nav_add_spot_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = strings.spotAGem,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Tab 4: Saved
                NavTabItem(
                    title = strings.saved,
                    selected = currentTab == MainTab.SAVED,
                    selectedIcon = Icons.Filled.Favorite,
                    unselectedIcon = Icons.Outlined.FavoriteBorder,
                    testTag = "nav_tab_saved",
                    onClick = { onTabSelected(MainTab.SAVED) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 5: Profile
                NavTabItem(
                    title = strings.profile,
                    selected = currentTab == MainTab.PROFILE,
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.PersonOutline,
                    testTag = "nav_tab_profile",
                    onClick = { onTabSelected(MainTab.PROFILE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(
    title: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    showDot: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) PrimaryContainer else Color.Transparent
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val animatedBg = animateColorAsState(targetValue = bgColor, label = "tabBg")
    val animatedContent = animateColorAsState(targetValue = contentColor, label = "tabContent")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(animatedBg.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = title,
                    tint = animatedContent.value,
                    modifier = Modifier.size(22.dp)
                )
                if (showDot && !selected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Secondary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = animatedContent.value
            )
        }
    }
}
