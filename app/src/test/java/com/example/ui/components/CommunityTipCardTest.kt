package com.example.ui.components

import com.example.ui.theme.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityTipCardTest {

    private val strings = EnglishStrings

    @Test
    fun resolveVotingNoteMessage_syncedAndMigratedTip_returnsNull() {
        val result = resolveVotingNoteMessage(isSyncedToCloud = true, isMigrated = true, strings = strings)
        assertNull("Synced and migrated tip should enable upvoting (return null message)", result)
    }

    @Test
    fun resolveVotingNoteMessage_unsyncedTip_returnsVotingAvailableAfterSync() {
        val result = resolveVotingNoteMessage(isSyncedToCloud = false, isMigrated = true, strings = strings)
        assertEquals("Unsynced tip must show 'Voting available after cloud sync' message", strings.votingAvailableAfterSync, result)
    }

    @Test
    fun resolveVotingNoteMessage_syncedUnmigratedTip_returnsCommunityVotingOpensAfterUpdate() {
        val result = resolveVotingNoteMessage(isSyncedToCloud = true, isMigrated = false, strings = strings)
        assertEquals("Synced unmigrated tip must show 'Voting opens after the community update' message", strings.communityVotingOpensAfterUpdate, result)
    }
}
