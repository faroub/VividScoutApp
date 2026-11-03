package com.example.util

import com.example.data.local.CommunityTipEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityTipRankerTest {

    private fun createTip(
        id: String,
        upvotes: Int,
        legacyUpvotes: Int = 0,
        timestamp: Long
    ): CommunityTipEntity {
        return CommunityTipEntity(
            id = id,
            spotId = "spot_1",
            spotTitle = "Miradouro",
            authorName = "Scout",
            content = "Great view",
            timestamp = timestamp,
            legacyUpvotes = legacyUpvotes,
            upvotes = upvotes
        )
    }

    @Test
    fun rankTipsByUsefulness_sortsByTotalUpvotesDescending_andBreaksTiesByRecency() {
        val tipA = createTip(id = "tip_A", upvotes = 5, legacyUpvotes = 2, timestamp = 1000L) // total = 7, old
        val tipB = createTip(id = "tip_B", upvotes = 10, legacyUpvotes = 0, timestamp = 2000L) // total = 10, newest
        val tipC = createTip(id = "tip_C", upvotes = 3, legacyUpvotes = 4, timestamp = 3000L) // total = 7, newer (tie with A)
        val tipD = createTip(id = "tip_D", upvotes = 1, legacyUpvotes = 0, timestamp = 1500L) // total = 1

        val ranked = CommunityTipRanker.rankTipsByUsefulness(listOf(tipA, tipD, tipB, tipC))

        assertEquals(4, ranked.size)
        assertEquals("Highest upvotes (10) must be first", "tip_B", ranked[0].id)
        assertEquals("Tie for 7 upvotes: newer timestamp (3000) comes before older (1000)", "tip_C", ranked[1].id)
        assertEquals("Tie for 7 upvotes: older timestamp (1000)", "tip_A", ranked[2].id)
        assertEquals("Lowest upvotes (1) must be last", "tip_D", ranked[3].id)
    }

    @Test
    fun partitionExpandedAndCollapsed_topThreeExpanded_restCollapsed() {
        val tips = (1..5).map { i ->
            createTip(id = "tip_$i", upvotes = i, timestamp = i * 1000L)
        }

        val (expanded, collapsed) = CommunityTipRanker.partitionExpandedAndCollapsed(tips)

        assertEquals("Top 3 tips must be expanded", 3, expanded.size)
        assertEquals("Remaining 2 tips must be collapsed", 2, collapsed.size)

        assertEquals("tip_5", expanded[0].id)
        assertEquals("tip_4", expanded[1].id)
        assertEquals("tip_3", expanded[2].id)

        assertEquals("tip_2", collapsed[0].id)
        assertEquals("tip_1", collapsed[1].id)
    }
}
