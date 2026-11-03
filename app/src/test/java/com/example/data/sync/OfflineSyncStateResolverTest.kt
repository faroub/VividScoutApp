package com.example.data.sync

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineSyncStateResolverTest {

    private fun createSpot(
        id: String = "spot_1",
        isOfflineDraft: Boolean = true,
        isDiscovered: Boolean = false,
        statusBadge: String = "Queued"
    ): ScoutSpotEntity {
        return ScoutSpotEntity(
            id = id,
            title = "Test Spot",
            description = "Description",
            category = "sights",
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = "",
            badgeLabel = "Local",
            statusBadge = statusBadge,
            tagsRaw = "",
            isOfflineDraft = isOfflineDraft,
            isDiscovered = isDiscovered
        )
    }

    @Test
    fun stateTransition_waitingToSyncingToSynced() {
        // Step 1: WAITING state
        val waitingSpot = createSpot(statusBadge = "Queued")
        val waitingItem = OfflineSyncStateResolver.resolveState(waitingSpot)
        assertEquals(OfflineSyncState.WAITING, waitingItem.state)
        assertNull(waitingItem.failureReasonPlainLanguage)

        // Step 2: SYNCING state
        val syncingSpot = createSpot(statusBadge = "Syncing...")
        val syncingItem = OfflineSyncStateResolver.resolveState(syncingSpot)
        assertEquals(OfflineSyncState.SYNCING, syncingItem.state)
        assertNull(syncingItem.failureReasonPlainLanguage)

        // Step 3: SYNCED state
        val syncedSpot = createSpot(isOfflineDraft = false, isDiscovered = true, statusBadge = "Synced")
        val syncedItem = OfflineSyncStateResolver.resolveState(syncedSpot)
        assertEquals(OfflineSyncState.SYNCED, syncedItem.state)
        assertNull(syncedItem.failureReasonPlainLanguage)
    }

    @Test
    fun stateTransition_waitingToSyncingToFailedToRetry() {
        // Step 1: WAITING state
        val waitingSpot = createSpot(statusBadge = "Queued")
        assertEquals(OfflineSyncState.WAITING, OfflineSyncStateResolver.resolveState(waitingSpot).state)

        // Step 2: SYNCING state
        val syncingSpot = createSpot(statusBadge = "Syncing...")
        assertEquals(OfflineSyncState.SYNCING, OfflineSyncStateResolver.resolveState(syncingSpot).state)

        // Step 3: FAILED state (e.g. Network error)
        val failedSpot = createSpot(statusBadge = "FAILED: Connection Timeout")
        val failedItem = OfflineSyncStateResolver.resolveState(failedSpot)
        assertEquals(OfflineSyncState.FAILED, failedItem.state)

        // Plain language failure reason must NOT contain raw exception
        assertNotNull(failedItem.failureReasonPlainLanguage)
        assertEquals("Server response timed out. Tap Retry to resend.", failedItem.failureReasonPlainLanguage)

        // Step 4: Retry transition back to SYNCING
        val retryingSpot = failedSpot.copy(statusBadge = "Syncing...")
        assertEquals(OfflineSyncState.SYNCING, OfflineSyncStateResolver.resolveState(retryingSpot).state)
    }

    @Test
    fun countWaitingAndFailed_sumsWaitingAndFailedItemsCorrectly() {
        val spots = listOf(
            createSpot(id = "s1", statusBadge = "Queued"), // WAITING
            createSpot(id = "s2", statusBadge = "FAILED: Network Offline"), // FAILED
            createSpot(id = "s3", isOfflineDraft = false, isDiscovered = true, statusBadge = "Synced"), // SYNCED
            createSpot(id = "s4", statusBadge = "Syncing...") // SYNCING
        )

        val count = OfflineSyncStateResolver.countWaitingAndFailed(spots)
        assertEquals("Count must sum WAITING (1) + FAILED (1) = 2", 2, count)
    }
}
