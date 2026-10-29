package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SurfaceContainerLowest

@Composable
fun VividScoutTopBar(
    onSearchClick: () -> Unit,
    onRadarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onToggleHeaderStyle: () -> Unit,
    onLanguageClick: () -> Unit,
    onGpsClick: () -> Unit = {},
    onArClick: () -> Unit = {},
    hasLocationPermission: Boolean = true,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        color = SurfaceContainerLowest.copy(alpha = 0.95f),
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Brand Logo & GPS Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .testTag("app_brand_header")
                    .clickable { onToggleHeaderStyle() }
            ) {
                // Stylized Scout Compass Badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandTeal, BrandCoral)
                            )
                        )
                        .padding(1.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(31.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF0D132D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = "VividScout Logo",
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        // Lisbon Live GPS Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (hasLocationPermission) SecondaryContainer.copy(alpha = 0.45f)
                                    else BrandCoral.copy(alpha = 0.15f)
                                )
                                .clickable { onGpsClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("topbar_gps_pill")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(if (hasLocationPermission) Secondary else BrandCoral)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (hasLocationPermission) strings.liveGpsStatus else "GPS Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasLocationPermission) MaterialTheme.colorScheme.onSecondaryContainer else BrandCoral,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = strings.explore,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right: Language picker, Radar status, style toggle, Search, Profile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Language selector pill button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SecondaryContainer.copy(alpha = 0.35f))
                        .clickable { onLanguageClick() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("language_switch_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Language",
                            tint = Secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = when (currentLang) {
                                com.example.ui.theme.AppLanguage.ENGLISH -> "EN"
                                com.example.ui.theme.AppLanguage.FRENCH -> "FR"
                                com.example.ui.theme.AppLanguage.ARABIC -> "عربي"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Secondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // AR Camera Mode button
                IconButton(
                    onClick = onArClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("ar_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AR Navigation Mode",
                        tint = BrandCoral,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Header style toggle
                IconButton(
                    onClick = onToggleHeaderStyle,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_header_style_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = "Switch View Style",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Radar sensor status
                IconButton(
                    onClick = onRadarClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("radar_status_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Radar Status",
                            tint = Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(SecondaryFixed)
                                .border(1.dp, SurfaceContainerLowest, CircleShape)
                        )
                    }
                }

                // Search button
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("search_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(2.dp, PrimaryContainer.copy(alpha = 0.35f), CircleShape)
                        .clickable { onProfileClick() }
                        .testTag("profile_avatar_button")
                ) {
                    AsyncImage(
                        model = "https://lh3.googleusercontent.com/aida-public/AB6AXuAs6vcDGG_z1rRQ0dZDwD-EyHaTOuW0LL_SjnChMzi9GQMjxBw0tunAYfTpCN8QILwZKh9-c7yMiwZWmEDmuSRz4e2Dw8DiwjGcvM6P1SEcmM9mE8sQvMkwqfLaWKKjJ-jy0DxycCtIvV6boGu1WI0mWlQUpYsB5ZW9SIcvgM1Z8400SklNsRhcjIRAgZBISZdPaEj10snj7R4T1tS2lkLc5UXILauYCvecoh7wm_NMKJFItk5uEP6-",
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}
