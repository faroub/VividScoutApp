package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePrefetchPolicyTest {

    @Test
    fun testWalkingSpeedHealthyBattery_optimalWalking() {
        val battery = BatteryStatus(levelPercent = 85, isPlugged = false, isPowerSaveMode = false)
        val speedMps = 1.3f // Typical pedestrian walking speed (~4.7 km/h)

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.OPTIMAL_WALKING, mode)
        assertTrue(mode.isPrefetchAllowed)
        assertEquals(15_000L, mode.locationIntervalMs)
        assertEquals(400f, mode.triggerDisplacementMeters, 0.1f)
    }

    @Test
    fun testVehicleSpeed_pausesPrefetchEvenWithHealthyBattery() {
        val battery = BatteryStatus(levelPercent = 95, isPlugged = false, isPowerSaveMode = false)
        val speedMps = 15.0f // Car / train speed (~54 km/h)

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.VEHICLE_SPEED_PAUSED, mode)
        assertFalse(mode.isPrefetchAllowed)
        assertEquals(60_000L, mode.locationIntervalMs)
    }

    @Test
    fun testVehicleSpeed_pausesPrefetchEvenWhenCharging() {
        val battery = BatteryStatus(levelPercent = 100, isPlugged = true, isPowerSaveMode = false)
        val speedMps = 8.5f // ~30.6 km/h (above VEHICLE_SPEED_THRESHOLD_MPS of 6.94 m/s)

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.VEHICLE_SPEED_PAUSED, mode)
        assertFalse(mode.isPrefetchAllowed)
    }

    @Test
    fun testLowBattery_triggersEcoMode() {
        val battery = BatteryStatus(levelPercent = 15, isPlugged = false, isPowerSaveMode = false)
        val speedMps = 1.2f

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.LOW_BATTERY_ECO, mode)
        assertTrue(mode.isPrefetchAllowed)
        // 3 minutes GPS polling interval
        assertEquals(180_000L, mode.locationIntervalMs)
        // 1km displacement before next query
        assertEquals(1000f, mode.triggerDisplacementMeters, 0.1f)
    }

    @Test
    fun testBoundaryBattery20Percent_triggersEcoMode() {
        val battery = BatteryStatus(levelPercent = 20, isPlugged = false, isPowerSaveMode = false)
        val speedMps = 1.0f

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.LOW_BATTERY_ECO, mode)
    }

    @Test
    fun testBattery21Percent_optimalWalking() {
        val battery = BatteryStatus(levelPercent = 21, isPlugged = false, isPowerSaveMode = false)
        val speedMps = 1.0f

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.OPTIMAL_WALKING, mode)
    }

    @Test
    fun testPowerSaveMode_triggersEcoMode() {
        val battery = BatteryStatus(levelPercent = 65, isPlugged = false, isPowerSaveMode = true)
        val speedMps = 1.1f

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.LOW_BATTERY_ECO, mode)
        assertEquals(180_000L, mode.locationIntervalMs)
        assertEquals(1000f, mode.triggerDisplacementMeters, 0.1f)
    }

    @Test
    fun testPluggedIn_unrestricted() {
        val battery = BatteryStatus(levelPercent = 12, isPlugged = true, isPowerSaveMode = false)
        val speedMps = 1.4f

        val mode = AdaptivePrefetchPolicy.evaluatePolicy(battery, speedMps)

        assertEquals(PrefetchPowerMode.CHARGING_UNRESTRICTED, mode)
        assertTrue(mode.isPrefetchAllowed)
        assertEquals(12_000L, mode.locationIntervalMs)
        assertEquals(350f, mode.triggerDisplacementMeters, 0.1f)
    }
}
