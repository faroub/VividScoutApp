package com.example.data.sync

import com.example.data.local.ScoutSpotEntity

enum class OfflineSyncState {
    WAITING,
    SYNCING,
    FAILED,
    SYNCED
}

data class OfflineSyncItem(
    val id: String,
    val title: String,
    val locationName: String,
    val state: OfflineSyncState,
    val failureReasonPlainLanguage: String? = null
)

object OfflineSyncStateResolver {

    fun resolveState(spot: ScoutSpotEntity): OfflineSyncItem {
        val badge = spot.statusBadge
        val state = when {
            !spot.isOfflineDraft && spot.statusBadge.contains("Synced", ignoreCase = true) -> OfflineSyncState.SYNCED
            badge.equals("Syncing...", ignoreCase = true) -> OfflineSyncState.SYNCING
            badge.startsWith("FAILED", ignoreCase = true) || badge.contains("Error", ignoreCase = true) -> OfflineSyncState.FAILED
            else -> OfflineSyncState.WAITING
        }

        val plainReason = if (state == OfflineSyncState.FAILED) {
            when {
                badge.contains("Auth", ignoreCase = true) || badge.contains("Permission", ignoreCase = true) ->
                    "Authentication required. Tap Retry to connect."
                badge.contains("Timeout", ignoreCase = true) ->
                    "Server response timed out. Tap Retry to resend."
                else ->
                    "Network connection unavailable. Tap Retry when connected."
            }
        } else null

        return OfflineSyncItem(
            id = spot.id,
            title = spot.title,
            locationName = spot.locationName,
            state = state,
            failureReasonPlainLanguage = plainReason
        )
    }

    fun countWaitingAndFailed(spots: List<ScoutSpotEntity>): Int {
        return spots.count { spot ->
            val resolved = resolveState(spot)
            resolved.state == OfflineSyncState.WAITING || resolved.state == OfflineSyncState.FAILED
        }
    }
}
