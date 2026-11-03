package com.example.util

import com.example.data.local.CommunityTipEntity

/**
 * Utility for ranking community tips by usefulness (upvotes descending, then recency for ties).
 */
object CommunityTipRanker {

    /**
     * Ranks community tips by total upvotes descending, breaking ties with timestamp descending (recency).
     */
    fun rankTipsByUsefulness(tips: List<CommunityTipEntity>): List<CommunityTipEntity> {
        return tips.sortedWith(
            compareByDescending<CommunityTipEntity> { it.totalUpvotes }
                .thenByDescending { it.timestamp }
        )
    }

    /**
     * Partitions ranked tips into top 3 expanded tips and remaining collapsed tips.
     */
    fun partitionExpandedAndCollapsed(tips: List<CommunityTipEntity>): Pair<List<CommunityTipEntity>, List<CommunityTipEntity>> {
        val ranked = rankTipsByUsefulness(tips)
        val expanded = ranked.take(3)
        val collapsed = if (ranked.size > 3) ranked.drop(3) else emptyList()
        return Pair(expanded, collapsed)
    }
}
