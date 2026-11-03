package com.example.util

data class LowPowerTrackingState(
    val isLowPowerActive: Boolean,
    val locationIntervalMs: Long,
    val isCompassEnabled: Boolean,
    val modeLabel: String
)

/**
 * Utility for evaluating low-power location tracking policy based on battery level thresholds,
 * charging state, and manual settings toggles.
 */
object LowPowerTrackingPolicy {

    const val DEFAULT_THRESHOLD_PERCENT = 20
    const val LOW_POWER_INTERVAL_MS = 30_000L // 30 seconds
    const val NORMAL_INTERVAL_MS = 5_000L // 5 seconds

    /**
     * Evaluates whether low-power tracking is active and determines tracking parameters.
     */
    fun evaluateTrackingState(
        batteryLevelPercent: Int,
        isCharging: Boolean = false,
        isManualToggleEnabled: Boolean = false,
        thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT
    ): LowPowerTrackingState {
        val isLowPowerActive = isManualToggleEnabled ||
                (batteryLevelPercent <= thresholdPercent && !isCharging)

        return if (isLowPowerActive) {
            LowPowerTrackingState(
                isLowPowerActive = true,
                locationIntervalMs = LOW_POWER_INTERVAL_MS,
                isCompassEnabled = false,
                modeLabel = "Low-Power GPS (30s polling, compass paused)"
            )
        } else {
            LowPowerTrackingState(
                isLowPowerActive = false,
                locationIntervalMs = NORMAL_INTERVAL_MS,
                isCompassEnabled = true,
                modeLabel = "High-Precision GPS (5s polling)"
            )
        }
    }
}
