package com.example.util

/**
 * Operating power and motion profile for the Proximity Prefetch Service.
 */
enum class PrefetchPowerMode(
    val title: String,
    val badgeLabel: String,
    val locationIntervalMs: Long,
    val minUpdateDistanceMeters: Float,
    val triggerDisplacementMeters: Float,
    val isPrefetchAllowed: Boolean
) {
    OPTIMAL_WALKING(
        title = "Optimal Walking",
        badgeLabel = "Walking • Active",
        locationIntervalMs = 15_000L, // 15 seconds
        minUpdateDistanceMeters = 25f,
        triggerDisplacementMeters = 400f,
        isPrefetchAllowed = true
    ),
    CHARGING_UNRESTRICTED(
        title = "Plugged In",
        badgeLabel = "Charging • Active",
        locationIntervalMs = 12_000L, // 12 seconds
        minUpdateDistanceMeters = 20f,
        triggerDisplacementMeters = 350f,
        isPrefetchAllowed = true
    ),
    LOW_BATTERY_ECO(
        title = "Eco Mode",
        badgeLabel = "Eco • 3m Polling",
        locationIntervalMs = 180_000L, // 3 minutes (180s)
        minUpdateDistanceMeters = 100f,
        triggerDisplacementMeters = 1000f, // 1km displacement before AI query
        isPrefetchAllowed = true
    ),
    VEHICLE_SPEED_PAUSED(
        title = "Transit Speed",
        badgeLabel = "Transit • Paused",
        locationIntervalMs = 60_000L, // 60 seconds
        minUpdateDistanceMeters = 100f,
        triggerDisplacementMeters = Float.MAX_VALUE, // Paused
        isPrefetchAllowed = false
    )
}

/**
 * Snapshot of device battery telemetry.
 */
data class BatteryStatus(
    val levelPercent: Int = 100,
    val isPlugged: Boolean = false,
    val isPowerSaveMode: Boolean = false
)

/**
 * Evaluates device power state and velocity to determine the optimal prefetch policy.
 */
object AdaptivePrefetchPolicy {

    /** Speed threshold in m/s (6.94 m/s ≈ 25 km/h). Speeds above this indicate trains, cars, or metro transit */
    const val VEHICLE_SPEED_THRESHOLD_MPS = 6.94f

    /** Low battery threshold percentage */
    const val LOW_BATTERY_THRESHOLD_PERCENT = 20

    /**
     * Evaluates the active power and motion profile given current battery metrics and movement speed.
     */
    fun evaluatePolicy(battery: BatteryStatus, speedMps: Float): PrefetchPowerMode {
        return when {
            // Speed takes priority: walking gems shouldn't be pre-cached when moving at train/car speeds
            speedMps > VEHICLE_SPEED_THRESHOLD_MPS -> PrefetchPowerMode.VEHICLE_SPEED_PAUSED
            battery.isPlugged -> PrefetchPowerMode.CHARGING_UNRESTRICTED
            battery.isPowerSaveMode || battery.levelPercent <= LOW_BATTERY_THRESHOLD_PERCENT -> PrefetchPowerMode.LOW_BATTERY_ECO
            else -> PrefetchPowerMode.OPTIMAL_WALKING
        }
    }
}
