package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.offline.GuidedWalkMood
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal

@Composable
fun GuidedWalkOptionsCard(
    selectedDurationMinutes: Int,
    selectedMood: GuidedWalkMood,
    onDurationSelected: (Int) -> Unit,
    onMoodSelected: (GuidedWalkMood) -> Unit
) {
    // Step 2: Duration & Mood Selection Card
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth().testTag("guided_walk_options_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Duration Selector
            Text(
                text = "Walk Duration",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(30, 60, 120).forEach { mins ->
                    val isSelected = selectedDurationMinutes == mins
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) BrandTeal else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (isSelected) BrandTeal else Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDurationSelected(mins) }
                            .testTag("duration_chip_$mins")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$mins min",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mood Selector
            Text(
                text = "Walk Mood",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuidedWalkMood.entries.forEach { mood ->
                    val isSelected = selectedMood == mood
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) BrandCoral else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (isSelected) BrandCoral else Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onMoodSelected(mood) }
                            .testTag("mood_chip_${mood.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mood.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}
