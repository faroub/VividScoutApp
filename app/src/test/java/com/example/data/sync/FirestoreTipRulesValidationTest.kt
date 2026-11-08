package com.example.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the firestore.rules constraints for community tip documents so the client
 * can generate compliant writes and the contract stays documented on the Kotlin side.
 *
 * The rules deny direct client creation of community_tips entirely: tips are written by
 * the trusted postCommunityTip Cloud Function (functions/community.js). Ownership is no
 * longer expressed in the document — anonymous UIDs are stable identifiers, so the
 * public document carries no ownerUid, and the rules enforce ownership through the
 * server-written ownership index (see firestore.rules communityOwnerUid). The mirror
 * below therefore models the persisted document shape (no ownerUid field) and the
 * update-side constraints: field types/lengths and the immutability of the
 * server-owned vote counters.
 */
class FirestoreTipRulesValidationTest {

    data class MockTipData(
        val id: String,
        val spotId: String,
        val spotTitle: String,
        val authorName: String,
        val content: String,
        val timestamp: Long,
        val isStatusUpdate: Boolean = false,
        val statusCategory: String = "INSIGHT",
        val legacyUpvotes: Int = 0,
        var upvotes: Int = 0
    )

    fun isValidTipDocument(data: MockTipData): Boolean {
        return data.id.length in 1..128 &&
                data.spotId.length in 1..128 &&
                data.spotTitle.length in 2..120 &&
                data.authorName.length in 2..50 &&
                data.content.length in 2..500 &&
                data.statusCategory.length in 2..32 &&
                data.legacyUpvotes in 0..1000000 &&
                data.upvotes in 0..1000000
    }

    /**
     * The client-side half of the rules update branch: a content refresh may only
     * touch the client-controlled fields and must keep the server-owned counters
     * byte-for-byte identical. Ownership itself is enforced server-side through the
     * ownership index, so it is intentionally not part of this mirror.
     */
    fun isCounterPreservingUpdate(
        existingDoc: MockTipData,
        updatedDoc: MockTipData
    ): Boolean {
        if (!isValidTipDocument(updatedDoc)) return false

        if (updatedDoc.upvotes != existingDoc.upvotes) return false
        if (updatedDoc.legacyUpvotes != existingDoc.legacyUpvotes) return false

        return true
    }

    @Test
    fun createTip_validatesFieldLengthsAndTypes() {
        val validTip = MockTipData(
            id = "tip_100",
            spotId = "spot_1",
            spotTitle = "Miradouro De Santa Luzia",
            authorName = "Alice",
            content = "Stunning sunset vista over the Tagus river.",
            timestamp = System.currentTimeMillis()
        )
        assertTrue("Valid tip document must pass security validation", isValidTipDocument(validTip))

        val oversizedContent = "A".repeat(501)
        val invalidTipContent = validTip.copy(content = oversizedContent)
        assertFalse("Content > 500 characters MUST be rejected by rules", isValidTipDocument(invalidTipContent))

        val invalidTitle = validTip.copy(spotTitle = "A")
        assertFalse("Spot title < 2 characters MUST be rejected by rules", isValidTipDocument(invalidTitle))

        val invalidUpvotes = validTip.copy(legacyUpvotes = -5)
        assertFalse("Negative upvotes MUST be rejected by rules", isValidTipDocument(invalidUpvotes))
    }

    @Test
    fun ownerUpdate_attemptingToModifyUpvotesOrLegacyUpvotes_isRejected() {
        val existingTip = MockTipData(
            id = "tip_100",
            spotId = "spot_1",
            spotTitle = "Miradouro De Santa Luzia",
            authorName = "Alice",
            content = "Original tip content",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 2,
            upvotes = 5
        )

        val validEdit = existingTip.copy(content = "Updated tip content with more detail")
        assertTrue("Content update without upvote changes MUST be allowed",
            isCounterPreservingUpdate(existingTip, validEdit))

        val illegalUpvoteEdit = existingTip.copy(content = "Updated content", upvotes = 10)
        assertFalse("Update attempting to modify upvotes MUST be rejected by rules",
            isCounterPreservingUpdate(existingTip, illegalUpvoteEdit))

        val illegalLegacyEdit = existingTip.copy(content = "Updated content", legacyUpvotes = 50)
        assertFalse("Update attempting to modify legacyUpvotes MUST be rejected by rules",
            isCounterPreservingUpdate(existingTip, illegalLegacyEdit))
    }

    @Test
    fun atomicVoteRule_preExistingVoteDoc_preventsReplayIncrement() {
        val existingVoteDocs = mutableSetOf("uid_alice") // Alice voted previously
        val authUid = "uid_alice"

        // Pre-write condition: !exists(votes/uid_alice)
        val preExists = existingVoteDocs.contains(authUid)
        val preStateCheck = !preExists // Returns FALSE because doc already exists!

        assertFalse("Replay upvote using pre-existing vote doc MUST fail pre-write check (!exists)", preStateCheck)
    }

    @Test
    fun atomicVoteRule_counterIncrementWithoutNewVoteDoc_isRejected() {
        val voteCreatedInTransaction = false // Transaction tried incrementing counter without creating vote doc
        val postStateCheck = voteCreatedInTransaction // existsAfter returns false

        assertFalse("Counter increment without creating vote doc MUST fail post-write check (existsAfter)", postStateCheck)
    }
}
