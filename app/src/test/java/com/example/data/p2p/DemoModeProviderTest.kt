package com.example.data.p2p

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoModeProviderTest {

    @Test
    fun evaluateDemoPeerSyncFlag_rejectsTrueValueWhenReleaseMode() {
        val resultInRelease = DemoModeProvider.evaluateDemoPeerSyncFlag(requestedValue = true, isDebug = false)
        assertFalse("Demo peer sync flag must strictly evaluate to false in release builds (isDebug = false)", resultInRelease)

        val falseResultInRelease = DemoModeProvider.evaluateDemoPeerSyncFlag(requestedValue = false, isDebug = false)
        assertFalse("Demo peer sync flag must evaluate to false when requested false in release builds", falseResultInRelease)
    }

    @Test
    fun evaluateDemoPeerSyncFlag_allowsTrueValueWhenDebugMode() {
        val resultInDebug = DemoModeProvider.evaluateDemoPeerSyncFlag(requestedValue = true, isDebug = true)
        assertTrue("Demo peer sync flag must evaluate to true in debug builds when requested true", resultInDebug)

        val falseResultInDebug = DemoModeProvider.evaluateDemoPeerSyncFlag(requestedValue = false, isDebug = true)
        assertFalse("Demo peer sync flag must evaluate to false in debug builds when requested false", falseResultInDebug)
    }

    @Test
    fun reset_clearsDemoPeerSyncEnabledFlag() {
        DemoModeProvider.reset()
        assertFalse("Reset must clear demo peer sync flag", DemoModeProvider.isDemoPeerSyncEnabled)
    }
}
