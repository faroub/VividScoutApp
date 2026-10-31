package com.example.data.p2p

/**
 * Global provider controlling simulated demo features.
 * Disabled by default in both debug and release builds.
 * When false, no code path pretends that peer-to-peer network discovery or synchronization occurred.
 */
object DemoModeProvider {

    @Volatile
    var isDemoPeerSyncEnabled: Boolean = false

    fun reset() {
        isDemoPeerSyncEnabled = false
    }
}
