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
    val manualOffline = PersistentProperty("manualOffline", false) // User chose offline in settings
    val serverReachable = Signal(false) // True when server is reachable while in manual offline
    val showingConnectivityDialog = Signal(false)
    val lastNetworkError = Signal<String?>(null)

    private var reconnectJob: Job? = null
    private var connectivityCheckJob: Job? = null
    private var consecutiveFailures = 0
    private const val FAILURES_BEFORE_DIALOG = 3

    fun onNetworkError(errorMessage: String) {
        lastNetworkError.value = errorMessage
        if (!offlineMode.value && !showingConnectivityDialog.value) {
            showingConnectivityDialog.value = true
        }
    }

    /** Enter offline mode due to network error (auto). Reconnect checks will auto-exit. */
    fun enterOfflineMode() {
        offlineMode.value = true
        showingConnectivityDialog.value = false
        stopConnectivityChecks()
        startReconnectChecks()
    }

    /** User manually toggled offline mode on from settings. Stays offline until user turns it off. */
    fun enterManualOfflineMode() {
        manualOffline.value = true
        offlineMode.value = true
        serverReachable.value = false
        showingConnectivityDialog.value = false
        stopConnectivityChecks()
        startReconnectChecks() // Will set serverReachable but NOT auto-exit
    }

    fun exitOfflineMode() {
        manualOffline.value = false
        offlineMode.value = false
        serverReachable.value = false
        lastNetworkError.value = null
        showingConnectivityDialog.value = false
        stopReconnectChecks()
        startConnectivityChecks()
    }

    fun dismissDialog() {
        showingConnectivityDialog.value = false
    }

    fun initialize() {
        if (offlineMode.value) {
            if (manualOffline.value) {
                // User chose offline — respect it, but check if server is reachable
                startReconnectChecks()
            } else {
                // Auto-offline from previous session — try to reconnect immediately
                AppScope.launch {
                    val success = jellyfinClient.value?.pingServer() ?: false
                    if (success) {
                        exitOfflineMode()
                    } else {
                        startReconnectChecks()
                    }
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
        consecutiveFailures = 0
        connectivityCheckJob = AppScope.launch {
            // Short initial delay for UI to register listeners
            delay(1_000)
            while (!offlineMode.value) {
                val client = jellyfinClient.value
                if (client != null) {
                    val reachable = client.pingServer()
                    if (reachable) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        // Only show dialog after multiple consecutive failures
                        // to avoid false positives from device sleep/wake transitions
                        if (consecutiveFailures >= FAILURES_BEFORE_DIALOG) {
                            onNetworkError("Unable to reach Jellyfin server")
                        }
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
            while (offlineMode.value) {
                val success = jellyfinClient.value?.pingServer() ?: false
                if (manualOffline.value) {
                    // Manual mode: just update the reachable flag, don't auto-exit
                    serverReachable.value = success
                } else {
                    // Auto mode: exit offline when server is reachable
                    if (success) {
                        exitOfflineMode()
                        break
                    }
                }
                delay(10_000)
            }
        }
    }

    private fun stopReconnectChecks() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
}
