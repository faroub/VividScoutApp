package com.example.ui.components

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.offline.GuidedWalkPlan
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal

@Composable
fun GuidedWalkPlanCard(
    guidedWalkPlan: GuidedWalkPlan,
    isPublishing: Boolean,
    publishError: String?,
    onPublish: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("guided_walk_plan_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Guided Walk Loop (${guidedWalkPlan.waypoints.size} waypoints)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    // Approximate plans carry graph or straight-line estimates:
                    // the summary must say so, not just the provenance line below.
                    text = if (guidedWalkPlan.isFullyRouted) {
                        "${guidedWalkPlan.formattedDistance} • ${guidedWalkPlan.totalWalkingMinutes} min walk"
                    } else {
                        "${guidedWalkPlan.formattedDistance} • ${guidedWalkPlan.totalWalkingMinutes} min walk (est.)"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = BrandTeal
                )
            }

            // Honest distance provenance: routed network distance vs approximate estimate.
            Text(
                text = guidedWalkPlan.routingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (guidedWalkPlan.isFullyRouted) BrandTeal else Color(0xFFF59E0B),
                fontSize = 11.sp
            )

            if (publishError != null) {
                Text(
                    text = publishError,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandCoral,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onPublish,
                enabled = !isPublishing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp).testTag("publish_share_itinerary_btn")
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publishing to Cloud...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish & Share Itinerary Link", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GuidedWalkEmptyStateCard(moodTitle: String, durationMinutes: Int) {
    // Step 3: Show Empty State if guidedWalkPlan is empty and no custom destination selected
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth().testTag("empty_guided_walk_plan_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Matching Spots Found",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No spots match the selected '$moodTitle' mood within $durationMinutes minutes. Try selecting a different mood or duration.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}
