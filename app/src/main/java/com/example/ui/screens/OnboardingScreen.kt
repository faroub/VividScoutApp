package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings

@Composable
fun OnboardingScreen(
    onExploreClick: () -> Unit,
    onSignInClick: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current

    val infiniteTransition = rememberInfiniteTransition(label = "badgeFloat")
    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandNight)
    ) {
        // Multi-Color Ambient Mesh Background Orbs
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = (-40).dp)
                .size(360.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0E4B5C).copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .blur(70.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 60.dp, y = (-20).dp)
                .size(320.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandCoral.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
                .blur(75.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
                .size(340.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandTeal.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                .blur(70.dp)
        )

        // Main Column Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar: Logo & Language & Skip Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("onboarding_logo")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D132D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                                    append("Vivid")
                                }
                                withStyle(SpanStyle(color = BrandTeal, fontWeight = FontWeight.ExtraBold)) {
                                    append("Scout")
                                }
                            },
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // Trailing: Language Switcher + Skip button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Language button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, BrandTeal.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                .clickable { onLanguageClick() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("onboarding_language_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Language",
                                    tint = BrandTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "EN"
                                        AppLanguage.FRENCH -> "FR"
                                        AppLanguage.ARABIC -> "عربي"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Skip button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                                .clickable { onExploreClick() }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("onboarding_skip_button")
                        ) {
                            Text(
                                text = strings.skip,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Kinetic Pagination Tracker
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step 1: Active elongated glowing teal pill
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(42.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(BrandTeal, Color(0xFF36EDD9))))
                    )
                    // Step 2, 3, 4
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }
            }

            // Central Visual Stage: Glowing Compass Container with Badges
            Box(
                modifier = Modifier
                    .size(290.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Aura behind compass
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    BrandTeal.copy(alpha = 0.4f),
                                    BrandCoral.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                        .blur(30.dp)
                )

                // Glassmorphic Card Container
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "https://lh3.googleusercontent.com/aida-public/AB6AXuCUkP19XPfTTBVM81Cs-4RjLfyNWSvfDaKj8HOfTl9vbeeV-JR0nykr-TdAA7kLBBUVc0R0-oOyhR1FVqCf5jyrfh_aqnfwvKTy_wgovfbopdjjQeSuJ4Bm5oZe2sgUXrdjZ0CWypWzHLFlmhQbvXIEVhCxRtVuvk9Xzk59_ZSZNrLiMNLrG6j4lbWxmI-pxYkcoSCxKnoSLB9BqjinrnvtvYMo0oYWdvigLcGC4k5JyAhX3dDrQesI",
                        contentDescription = "3D Compass Illustration",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(240.dp)
                            .testTag("onboarding_compass_image")
                    )
                }

                // Floating Badge 1: Top Left - Real-time radar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (floatOffset1).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0D132D).copy(alpha = 0.88f))
                        .border(1.dp, BrandTeal.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandTeal)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "⚡ ${strings.realTimeRadar}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                // Floating Badge 2: Right Middle - 10m radius
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 10.dp, y = (-20 + floatOffset2).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0D132D).copy(alpha = 0.88f))
                        .border(1.dp, BrandCoral.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandCoral)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "📍 ${strings.tenMRadius}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                // Floating Badge 3: Bottom Left - Unbeaten paths
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 12.dp, y = (10 + floatOffset1).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0D132D).copy(alpha = 0.88f))
                        .border(1.dp, Color(0xFFC084FC).copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFC084FC))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "✨ ${strings.unbeatenPaths}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Bottom Content & Primary Action
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Headline
                Text(
                    text = strings.discoverTheUnseen,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = strings.onboardingSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category Micro-Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicroCategoryChip(
                        name = strings.sights,
                        color = Color(0xFF34D399),
                        bgColor = Color(0xFF064E3B).copy(alpha = 0.5f)
                    )
                    MicroCategoryChip(
                        name = strings.streetEats,
                        color = Color(0xFFFBBF24),
                        bgColor = Color(0xFF78350F).copy(alpha = 0.5f)
                    )
                    MicroCategoryChip(
                        name = strings.gothicHistory,
                        color = Color(0xFFC084FC),
                        bgColor = Color(0xFF581C87).copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // CTA Button: "Let's Explore! ✨ →"
                Button(
                    onClick = onExploreClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(999.dp),
                            spotColor = BrandCoral.copy(alpha = 0.6f)
                        )
                        .testTag("onboarding_explore_button"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandCoral,
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = strings.letsExplore,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sign In Link
                Row(
                    modifier = Modifier
                        .clickable { onSignInClick() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.alreadyAScout} ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = strings.signIn,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = BrandTeal
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroCategoryChip(
    name: String,
    color: Color,
    bgColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                fontSize = 11.sp
            )
        }
    }
}
