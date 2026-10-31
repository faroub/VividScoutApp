package com.example.util

import androidx.compose.ui.graphics.Color

/**
 * Visual decay tiers for spot status updates and crowd-sourced tips.
 * Statuses decay over 24 hours to signal freshness without losing historical context.
 */
enum class TipDecayTier(
    val displayName: String,
    val containerColor: Color,
    val contentColor: Color,
    val dotColor: Color,
    val isFreshOrActive: Boolean
) {
    FRESH(
        displayName = "Fresh",
        containerColor = Color(0xFF10B981).copy(alpha = 0.16f), // Emerald
        contentColor = Color(0xFF059669),
        dotColor = Color(0xFF10B981),
        isFreshOrActive = true
    ),
    ACTIVE(
        displayName = "Active",
        containerColor = Color(0xFF0D9488).copy(alpha = 0.16f), // Teal
        contentColor = Color(0xFF0F766E),
        dotColor = Color(0xFF14B8A6),
        isFreshOrActive = true
    ),
    AGING(
        displayName = "Aging",
        containerColor = Color(0xFFF59E0B).copy(alpha = 0.16f), // Amber
        contentColor = Color(0xFFD97706),
        dotColor = Color(0xFFF59E0B),
        isFreshOrActive = false
    ),
    EXPIRED(
        displayName = "Expired",
        containerColor = Color(0xFF64748B).copy(alpha = 0.14f), // Slate Gray
        contentColor = Color(0xFF64748B),
        dotColor = Color(0xFF94A3B8),
        isFreshOrActive = false
    )
}

object TipDecayUtils {

    const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
    const val EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L
    const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

    /**
     * Calculates the decay tier based on age relative to [now].
     */
    fun calculateDecayTier(
        timestamp: Long,
        now: Long = System.currentTimeMillis()
    ): TipDecayTier {
        val ageMs = (now - timestamp).coerceAtLeast(0L)
        return when {
            ageMs <= TWO_HOURS_MS -> TipDecayTier.FRESH
            ageMs <= EIGHT_HOURS_MS -> TipDecayTier.ACTIVE
            ageMs <= TWENTY_FOUR_HOURS_MS -> TipDecayTier.AGING
            else -> TipDecayTier.EXPIRED
        }
    }

    /**
     * Formats elapsed duration into concise human-readable text.
     */
    fun formatRelativeTime(
        timestamp: Long,
        now: Long = System.currentTimeMillis()
    ): String {
        val diffMs = (now - timestamp).coerceAtLeast(0L)
        val minutes = diffMs / (60 * 1000L)
        val hours = diffMs / (60 * 60 * 1000L)
        val days = diffMs / (24 * 60 * 60 * 1000L)

        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "yesterday"
            else -> "${days}d ago"
        }
    }

    /**
     * Combines decay tier badge label with relative time.
     */
    fun getDecayBadgeText(
        timestamp: Long,
        now: Long = System.currentTimeMillis()
    ): String {
        val tier = calculateDecayTier(timestamp, now)
        val relative = formatRelativeTime(timestamp, now)
        return "${tier.displayName} • $relative"
    }
}
