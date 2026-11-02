package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * System ConnectivityManager network state observer.
 * Listens for network state transitions (cellular/Wi-Fi connection gained/lost)
 * and emits a reconnection event whenever the device transitions from offline to online.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isOnline = MutableStateFlow(checkInitialOnlineState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _onReconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onReconnected: SharedFlow<Unit> = _onReconnected.asSharedFlow()

    private var wasOffline = !checkInitialOnlineState()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network connection restored (onAvailable)")
            val previousState = _isOnline.value
            _isOnline.value = true

            if (!previousState || wasOffline) {
                wasOffline = false
                Log.i(TAG, "Triggering network reconnected event for background sync...")
                scope.launch {
                    _onReconnected.emit(Unit)
                }
            }
        }

        override fun onLost(network: Network) {
            Log.w(TAG, "Network connection lost (onLost)")
            _isOnline.value = false
            wasOffline = true
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "Network capabilities changed: hasInternet=$hasInternet")
            val previousState = _isOnline.value
            _isOnline.value = hasInternet

            if (hasInternet && (!previousState || wasOffline)) {
                wasOffline = false
                Log.i(TAG, "Triggering network reconnected event for background sync (validated capability)...")
                scope.launch {
                    _onReconnected.emit(Unit)
                }
            } else if (!hasInternet) {
                wasOffline = true
            }
        }
    }

    init {
        registerCallback()
    }

    private fun registerCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.i(TAG, "NetworkMonitor callback registered successfully. Initial online state: ${_isOnline.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}", e)
        }
    }

    fun unregisterCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.i(TAG, "NetworkMonitor callback unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback: ${e.message}")
        }
    }

    private fun checkInitialOnlineState(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
