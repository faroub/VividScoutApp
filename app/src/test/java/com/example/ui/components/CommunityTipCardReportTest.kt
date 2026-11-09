package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.local.CommunityTipEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the community tip card's report (flag) action: the flag exists
 * only for real, cloud-synced, migrated tips, invokes the callback with the exact
 * tip id, and stays absent for sample/demo tips and local-only drafts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommunityTipCardReportTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun reportableTip() = CommunityTipEntity(
        id = "user_tip_abc",
        spotId = "spot_1",
        spotTitle = "Miradouro",
        authorName = "@someone",
        content = "Great viewpoint at sunset.",
        timestamp = 1_750_000_000_000L,
        isLocallyAuthored = true,
        isSyncedToCloud = true
    )

    private fun setContentWithTip(
        tip: CommunityTipEntity,
        onReportClick: ((String) -> Unit)? = { }
    ) {
        composeRule.setContent {
            MaterialTheme {
                CommunityTipCard(
                    tip = tip,
                    onUpvoteClick = { },
                    onReportClick = onReportClick
                )
            }
        }
    }

    @Test
    fun reportFlag_isDisplayedForReportableTips() {
        setContentWithTip(reportableTip())
        composeRule.onNodeWithTag("report_tip_user_tip_abc").assertIsDisplayed()
    }

    @Test
    fun reportFlag_invokesCallbackWithTheTipId() {
        var reportedId: String? = null
        setContentWithTip(reportableTip()) { reportedId = it }
        composeRule.onNodeWithTag("report_tip_user_tip_abc").performClick()
        assertEquals("user_tip_abc", reportedId)
    }

    @Test
    fun reportFlag_isAbsentForSampleTips() {
        val sample = reportableTip().copy(
            id = "seed_tip_1",
            isLocallyAuthored = false,
            peerNodeId = "peer_seed_1"
        )
        setContentWithTip(sample)
        composeRule.onNodeWithTag("report_tip_seed_tip_1").assertDoesNotExist()
    }

    @Test
    fun reportFlag_isAbsentForUnsyncedLocalTips() {
        setContentWithTip(reportableTip().copy(isSyncedToCloud = false))
        composeRule.onNodeWithTag("report_tip_user_tip_abc").assertDoesNotExist()
    }

    @Test
    fun reportFlag_isAbsentWhenNoCallbackIsProvided() {
        composeRule.setContent {
            MaterialTheme {
                CommunityTipCard(
                    tip = reportableTip(),
                    onUpvoteClick = { }
                )
            }
        }
        composeRule.onNodeWithTag("report_tip_user_tip_abc").assertDoesNotExist()
    }
}
