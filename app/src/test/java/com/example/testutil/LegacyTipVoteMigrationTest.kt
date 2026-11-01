package com.example.testutil

import com.example.data.local.CommunityTipEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyTipVoteMigrationTest {

    data class SyntheticVoteRecord(
        val uid: String,
        val timestamp: Long
    )

    /**
     * Verifies the backfill logic for calculating synthetic vote records for legacy tips.
     */
    @Test
    fun calculateLegacyVoteBackfill_generatesCorrectNumberOfVoteDocuments() {
        val legacyTip = CommunityTipEntity(
            id = "seed_tip_01",
            spotId = "spot_1",
            spotTitle = "Miradouro de Santa Luzia",
            authorName = "@marco_lisbon",
            content = "Great quiet nook.",
            timestamp = 1700000000000L,
            upvotes = 5
        )

        val existingVoteDocumentIds = listOf("user_123") // 1 existing vote
        val missingVotesCount = legacyTip.upvotes - existingVoteDocumentIds.size

        val syntheticVotes = mutableListOf<SyntheticVoteRecord>()
        for (i in 1..missingVotesCount) {
            val legacyUid = "legacy_voter_${legacyTip.id}_${i}"
            syntheticVotes.add(SyntheticVoteRecord(uid = legacyUid, timestamp = legacyTip.timestamp))
        }

        assertEquals(4, syntheticVotes.size)
        assertEquals("legacy_voter_seed_tip_01_1", syntheticVotes.first().uid)
        assertEquals(1700000000000L, syntheticVotes.first().timestamp)
    }
}
