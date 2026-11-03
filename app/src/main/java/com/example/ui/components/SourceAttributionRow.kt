package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PlaceAttributionData(
    val displayText: String,
    val sourceUrl: String?
)

/**
 * Formats data source attributions according to standard licensing guidelines:
 * - "© OpenStreetMap contributors" for OSM data
 * - "Wikidata (CC0)" for Wikidata items
 * - "Wikipedia (CC BY-SA)" for Wikipedia text and images
 */
fun formatPlaceAttribution(
    sourceNameOrTag: String?,
    license: String? = null,
    sourceUrl: String? = null
): PlaceAttributionData {
    val tagLower = (sourceNameOrTag ?: "").lowercase()
    val licenseLower = (license ?: "").lowercase()

    val displayText = when {
        tagLower.contains("osm") || tagLower.contains("openstreetmap") || tagLower.contains("overpass") -> {
            "© OpenStreetMap contributors"
        }
        tagLower.contains("wikidata") || licenseLower.contains("cc0") -> {
            "Wikidata (CC0)"
        }
        tagLower.contains("wikipedia") || licenseLower.contains("by-sa") -> {
            "Wikipedia (CC BY-SA)"
        }
        sourceNameOrTag.isNullOrBlank() -> {
            "© OpenStreetMap contributors"
        }
        else -> {
            "$sourceNameOrTag${if (!license.isNullOrBlank()) " ($license)" else ""}"
        }
    }

    val defaultUrl = when {
        tagLower.contains("wikidata") -> "https://www.wikidata.org"
        tagLower.contains("wikipedia") -> "https://www.wikipedia.org"
        else -> "https://www.openstreetmap.org/copyright"
    }

    return PlaceAttributionData(
        displayText = displayText,
        sourceUrl = if (!sourceUrl.isNullOrBlank()) sourceUrl else defaultUrl
    )
}

/**
 * Small attribution row displaying source name, license, and clickable link.
 */
@Composable
fun SourceAttributionRow(
    sourceNameOrTag: String?,
    modifier: Modifier = Modifier,
    license: String? = null,
    sourceUrl: String? = null
) {
    val uriHandler = LocalUriHandler.current
    val attribution = formatPlaceAttribution(sourceNameOrTag, license, sourceUrl)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                attribution.sourceUrl?.let { url ->
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {
                        // Safe fallback if browser unavailable
                    }
                }
            }
            .padding(vertical = 2.dp, horizontal = 4.dp)
            .testTag("source_attribution_row")
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Source License Info",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = attribution.displayText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}
