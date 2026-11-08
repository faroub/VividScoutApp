package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScoutSpotEntity
import com.example.ui.components.SourceAttributionRow
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLow

@Composable
fun SpotDetailHistoryTabContent(spot: ScoutSpotEntity) {
    // History & Knowledge Tab Content
    val currentLangCode = LocalAppLanguage.current.code
    val hasWikipediaExtract = spot.description.length > 80 && !spot.description.contains("cataloged on Wikidata", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasWikipediaExtract) {
            Text(
                text = "WIKIPEDIA HISTORICAL EXTRACT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                ),
                color = BrandTeal
            )
            Text(
                text = spot.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
            SourceAttributionRow(
                sourceNameOrTag = "Wikipedia (${currentLangCode.uppercase()})",
                license = "CC BY-SA 4.0",
                sourceUrl = "https://en.wikipedia.org/wiki/${spot.title.replace(" ", "_")}"
            )
        } else {
            // Fallback when no Wikipedia article exists: short message + Wikidata description
            Text(
                text = "WIKIDATA KNOWLEDGE GRAPH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                ),
                color = Secondary
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Detailed Wikipedia article unavailable for this landmark.",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = spot.description.ifBlank { "Historic & cultural landmark cataloged on Wikidata Knowledge Graph." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SourceAttributionRow(
                sourceNameOrTag = "Wikidata Knowledge Graph",
                license = "CC0 1.0 Universal",
                sourceUrl = "https://www.wikidata.org"
            )
        }
    }
}

@Composable
fun SpotDetailWikidataContextCard(spot: ScoutSpotEntity) {
    // Wikidata Historical Context Card (if spot has Wikidata tags/status)
    val isWikidataSpot = spot.tagsRaw.contains("wikidata", ignoreCase = true) ||
            spot.statusBadge.contains("Wikidata", ignoreCase = true) ||
            spot.badgeLabel.contains("Wikidata", ignoreCase = true)

    if (!isWikidataSpot) return

    val tagParts = spot.tagsRaw.split("|||")
    val qid = tagParts.getOrNull(1)?.takeIf { it.startsWith("Q") } ?: "Knowledge Graph"
    val commonsMediaId = tagParts.find { it.startsWith("File:") || it.startsWith("Image:") } ?: ""

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wikidata_historical_context_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Historical Context",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "QID: $qid",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = spot.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            if (commonsMediaId.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Wikimedia Commons: $commonsMediaId",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
