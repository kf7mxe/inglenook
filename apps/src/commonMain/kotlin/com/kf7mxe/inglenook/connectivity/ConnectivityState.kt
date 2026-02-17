package com.kf7mxe.inglenook.connectivity

import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Connectivity
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ConnectivityState {
    val offlineMode = PersistentProperty("offlineMode", false)
    val showingConnectivityDialog = Signal(false)
    val lastNetworkError = Signal<String?>(null)

    private var reconnectJob: Job? = null
    private var connectivityCheckJob: Job? = null

    fun onNetworkError(errorMessage: String) {
        lastNetworkError.value = errorMessage
        if (!offlineMode.value && !showingConnectivityDialog.value) {
            showingConnectivityDialog.value = true
        }
    }

    fun enterOfflineMode() {
        offlineMode.value = true
        showingConnectivityDialog.value = false
        stopConnectivityChecks()
        startReconnectChecks()
    }

    fun exitOfflineMode() {
        offlineMode.value = false
        lastNetworkError.value = null
        showingConnectivityDialog.value = false
        stopReconnectChecks()
        startConnectivityChecks()
    }

    fun dismissDialog() {
        showingConnectivityDialog.value = false
    }

    fun initialize() {
        // If app was persisted as offline, do an immediate connectivity check
        // to avoid staying stuck in offline mode after restart
        if (offlineMode.value) {
            AppScope.launch {
                val success = jellyfinClient.value?.pingServer() ?: false
                if (success) {
                    exitOfflineMode()
                } else {
                    startReconnectChecks()
                }
            }
        } else {
            startConnectivityChecks()
        }

        // Listen to KiteUI's built-in connectivity detection
        Connectivity.lastConnectivityIssueCode.addListener {
            val code = Connectivity.lastConnectivityIssueCode.value
            if (code != 0.toShort()) {
                onNetworkError("Connection issue (HTTP $code)")
            }
        }
    }

    /** Periodically pings the server to detect connectivity loss while online. */
    private fun startConnectivityChecks() {
        connectivityCheckJob?.cancel()
        connectivityCheckJob = AppScope.launch {
            // Short initial delay for UI to register listeners
            delay(1_000)
            while (!offlineMode.value) {
                val client = jellyfinClient.value
                if (client != null) {
                    val reachable = client.pingServer()
                    if (!reachable) {
                        onNetworkError("Unable to reach Jellyfin server")
                    }
                }
                delay(10_000) // Check every 10 seconds
            }
        }
    }

    private fun stopConnectivityChecks() {
        connectivityCheckJob?.cancel()
        connectivityCheckJob = null
    }

    private fun startReconnectChecks() {
        reconnectJob?.cancel()
        reconnectJob = AppScope.launch {
            // Immediate first check, then periodic
            while (offlineMode.value) {
                val success = jellyfinClient.value?.pingServer() ?: false
                if (success) {
                    exitOfflineMode()
                    break
                }
                delay(10_000) // Check every 10 seconds
            }
        }
    }

    private fun stopReconnectChecks() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
}
