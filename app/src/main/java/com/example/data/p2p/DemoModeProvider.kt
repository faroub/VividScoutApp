package com.example.data.p2p

import com.example.BuildConfig

/**
 * Global provider controlling simulated demo features.
 * Disabled by default in both debug and release builds.
 * When false, no code path pretends that peer-to-peer network discovery or synchronization occurred.
 * Demo mode can only be enabled when BuildConfig.DEBUG is true.
 */
object DemoModeProvider {

    @Volatile
    private var _isDemoPeerSyncEnabled: Boolean = false

    var isDemoPeerSyncEnabled: Boolean
        get() = _isDemoPeerSyncEnabled
        set(value) {
            _isDemoPeerSyncEnabled = evaluateDemoPeerSyncFlag(value, BuildConfig.DEBUG)
        }

    internal fun evaluateDemoPeerSyncFlag(requestedValue: Boolean, isDebug: Boolean): Boolean {
        return if (isDebug) requestedValue else false
    }

    fun reset() {
        _isDemoPeerSyncEnabled = false
    }
}
