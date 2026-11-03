package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowPowerTrackingPolicyTest {

    @Test
    fun evaluateTrackingState_batteryBelowThreshold_activatesThirtySecondIntervalAndPausesCompass() {
        val state15 = LowPowerTrackingPolicy.evaluateTrackingState(
            batteryLevelPercent = 15,
            isCharging = false,
            isManualToggleEnabled = false
        )

        assertTrue("Battery at 15% (<= 20%) must activate low-power tracking", state15.isLowPowerActive)
        assertEquals(30_000L, state15.locationIntervalMs)
        assertFalse("Compass sensor must be disabled in low-power mode", state15.isCompassEnabled)
    }

    @Test
    fun evaluateTrackingState_exactThresholdBoundary_activatesLowPower() {
        val state20 = LowPowerTrackingPolicy.evaluateTrackingState(
            batteryLevelPercent = 20,
            isCharging = false
        )

        assertTrue("Battery at 20% exact threshold must activate low-power tracking", state20.isLowPowerActive)
        assertEquals(30_000L, state20.locationIntervalMs)
    }

    @Test
    fun evaluateTrackingState_batteryAboveThreshold_usesNormalHighPrecisionTracking() {
        val state80 = LowPowerTrackingPolicy.evaluateTrackingState(
            batteryLevelPercent = 80,
            isCharging = false
        )

        assertFalse("Battery at 80% should use normal high-precision tracking", state80.isLowPowerActive)
        assertEquals(5_000L, state80.locationIntervalMs)
        assertTrue("Compass sensor must remain active in normal mode", state80.isCompassEnabled)
    }

    @Test
    fun evaluateTrackingState_chargingOverridesLowBatteryThreshold() {
        val stateCharging15 = LowPowerTrackingPolicy.evaluateTrackingState(
            batteryLevelPercent = 15,
            isCharging = true
        )

        assertFalse("Charging device at 15% battery should override low-power mode", stateCharging15.isLowPowerActive)
        assertEquals(5_000L, stateCharging15.locationIntervalMs)
        assertTrue(stateCharging15.isCompassEnabled)
    }

    @Test
    fun evaluateTrackingState_manualToggleOverridesBatteryLevel() {
        val stateManual = LowPowerTrackingPolicy.evaluateTrackingState(
            batteryLevelPercent = 90,
            isCharging = false,
            isManualToggleEnabled = true
        )

        assertTrue("Manual toggle ON must activate low-power mode regardless of battery level", stateManual.isLowPowerActive)
        assertEquals(30_000L, stateManual.locationIntervalMs)
        assertFalse(stateManual.isCompassEnabled)
    }
}
