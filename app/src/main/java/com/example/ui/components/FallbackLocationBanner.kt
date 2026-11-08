package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppStrings

/**
 * Warning banner shown when discovery is silently running on the built-in default
 * (Lisbon) location because device location is off and the user never picked a city.
 * Tapping it opens the city picker.
 */
@Composable
fun FallbackLocationBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAction: Boolean = true,
    testTag: String = "fallback_location_banner"
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = "📍",
                fontSize = 18.sp,
                color = Color(0xFF92400E)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.fallbackLocationBannerText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Color(0xFF92400E),
                modifier = Modifier.weight(1f)
            )
            if (showAction) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFD97706)
                ) {
                    Text(
                        text = strings.fallbackLocationBannerAction,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
