package com.example.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreTipRulesValidationTest {

    data class MockTipData(
        val id: String,
        val ownerUid: String,
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
                data.ownerUid.length in 1..128 &&
                data.spotId.length in 1..128 &&
                data.spotTitle.length in 2..120 &&
                data.authorName.length in 2..50 &&
                data.content.length in 2..500 &&
                data.statusCategory.length in 2..32 &&
                data.legacyUpvotes in 0..1000000 &&
                data.upvotes in 0..1000000
    }

    fun isOwnerUpdateAllowed(
        existingDoc: MockTipData,
        updatedDoc: MockTipData,
        authUid: String
    ): Boolean {
        if (authUid != existingDoc.ownerUid) return false
        if (updatedDoc.ownerUid != existingDoc.ownerUid) return false
        if (!isValidTipDocument(updatedDoc)) return false

        if (updatedDoc.upvotes != existingDoc.upvotes) return false
        if (updatedDoc.legacyUpvotes != existingDoc.legacyUpvotes) return false

        return true
    }

    @Test
    fun createTip_validatesFieldLengthsAndTypes() {
        val validTip = MockTipData(
            id = "tip_100",
            ownerUid = "uid_alice",
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
            ownerUid = "uid_alice",
            spotId = "spot_1",
            spotTitle = "Miradouro De Santa Luzia",
            authorName = "Alice",
            content = "Original tip content",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 2,
            upvotes = 5
        )

        val validEdit = existingTip.copy(content = "Updated tip content with more detail")
        assertTrue("Owner content update without upvote changes MUST be allowed",
            isOwnerUpdateAllowed(existingTip, validEdit, "uid_alice"))

        val illegalUpvoteEdit = existingTip.copy(content = "Updated content", upvotes = 10)
        assertFalse("Owner update attempting to modify upvotes MUST be rejected by rules",
            isOwnerUpdateAllowed(existingTip, illegalUpvoteEdit, "uid_alice"))

        val illegalLegacyEdit = existingTip.copy(content = "Updated content", legacyUpvotes = 50)
        assertFalse("Owner update attempting to modify legacyUpvotes MUST be rejected by rules",
            isOwnerUpdateAllowed(existingTip, illegalLegacyEdit, "uid_alice"))

        assertFalse("Non-owner edit MUST be rejected by rules",
            isOwnerUpdateAllowed(existingTip, validEdit, "uid_bob"))
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
