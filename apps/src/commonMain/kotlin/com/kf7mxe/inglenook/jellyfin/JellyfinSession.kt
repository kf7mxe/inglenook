@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.jellyfin

import com.kf7mxe.inglenook.JellyfinServerConfig
import com.kf7mxe.inglenook.cache.ApiCache
import com.kf7mxe.inglenook.cache.CacheRefresher
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

// --- Multi-server storage ---

/** All configured Jellyfin servers (persisted across restarts). */
val jellyfinServers = PersistentProperty<List<JellyfinServerConfig>>("jellyfinServers", emptyList())

/** ID (_id.toString()) of the currently active server. */
val activeServerId = PersistentProperty<String?>("activeServerId", null)

// --- Derived signals for backward compatibility ---

/** The currently active server config. Most of the app reads this. */
val jellyfinServerConfig: Signal<JellyfinServerConfig?> = Signal<JellyfinServerConfig?>(null).also {
    migrateLegacyIfNeeded()
    val id = activeServerId.value
    it.value = if (id != null) jellyfinServers.value.find { s -> s._id.toString() == id } else null
}

/** The JellyfinClient for the active server. */
val jellyfinClient: Signal<JellyfinClient?> = Signal<JellyfinClient?>(null)

// --- Per-server scoped properties ---

@PublishedApi
internal val scopedPropertyCache = mutableMapOf<String, PersistentProperty<*>>()

/** Returns a PersistentProperty scoped to the active server, cached for stable reactive bindings. */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> serverScopedProperty(baseKey: String, default: T): PersistentProperty<T> {
    val serverKey = activeServerId.value ?: "default"
    val fullKey = "${baseKey}_${serverKey}"
    return scopedPropertyCache.getOrPut(fullKey) {
        PersistentProperty(fullKey, default)
    } as PersistentProperty<T>
}

/** Selected library IDs scoped to the active server. */
val selectedLibraryIds: PersistentProperty<List<String>>
    get() = serverScopedProperty("selectedLibraryIds", emptyList())

/** Whether diagnostic/crash report collection is enabled (off by default). */

/** Whether the user has been shown the diagnostics opt-in prompt. */
val hasSeenDiagnosticsPrompt = PersistentProperty("hasSeenDiagnosticsPrompt", false)

// --- Server management functions ---

/** Add a new server config and make it the active server. */
fun addServer(config: JellyfinServerConfig) {
    if (config.accessToken != null) {
        resetSessionExpiryGuard()
    }
    // Remove any existing config for the same server+user combo to avoid duplicates
    val existing = jellyfinServers.value.filter {
        !(it.serverUrl == config.serverUrl && it.userId == config.userId)
    }
    jellyfinServers.value = existing + config
    switchToServer(config._id.toString())
}

/** Refreshes server capabilities (permissions, plugin support) for the active server config and persists the result. */
fun refreshServerCapabilities(config: JellyfinServerConfig) {
    AppScope.launch {
        try {
            val client = jellyfinClient.invoke() ?: return@launch
            val canEditCollection = client.getCanEditCollection()
            val identifyAvailable = client.isIdentifyAvailable()
            val bookshelvesAvailable = client.bookshelfEndpointAvailable()

            if (canEditCollection != config.canEditCollection || identifyAvailable != config.identifyAvailable || bookshelvesAvailable != config.bookshelvesAvailable) {
                val updated = config.copy(
                    canEditCollection = canEditCollection,
                    identifyAvailable = identifyAvailable,
                    bookshelvesAvailable = bookshelvesAvailable
                )
                jellyfinServers.value = jellyfinServers.value.map {
                    if (it._id == updated._id) updated else it
                }
                jellyfinServerConfig.value = updated
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }
}

/** Switch the active server. Stops playback, clears cache, reinitializes client. */
fun switchToServer(serverId: String) {
    val config = jellyfinServers.value.find { it._id.toString() == serverId } ?: return

    // Stop any active playback (it's tied to the old server)
    PlaybackState.stop()

    // Close the old client to release stale HTTP connections
    jellyfinClient.value?.close()

    // Clear in-memory API cache
    ApiCache.clear()

    // Update active server
    activeServerId.value = serverId
    jellyfinServerConfig.value = config

    // Reset connectivity state so the new server gets a clean slate
    ConnectivityState.exitOfflineMode()

    if (config.accessToken != null) {
        // Reinitialize client
        jellyfinClient.value = JellyfinClient(
            serverUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId,
            deviceId = config.deviceId
        )

        CacheRefresher.start()

        refreshServerCapabilities(config)
    } else {
        // Logged-out server: no client to make authenticated requests with
        jellyfinClient.value = null
        CacheRefresher.stop()
    }
}

/** Remove a server from the list. If it's the active server, switch to another or clear. */
fun removeServer(serverId: String) {
    jellyfinServers.value = jellyfinServers.value.filter { it._id.toString() != serverId }

    if (activeServerId.value == serverId) {
        // Close the old client to release stale HTTP connections
        jellyfinClient.value?.close()

        val remaining = jellyfinServers.value
        if (remaining.isNotEmpty()) {
            switchToServer(remaining.first()._id.toString())
        } else {
            activeServerId.value = null
            jellyfinServerConfig.value = null
            jellyfinClient.value = null
            ConnectivityState.exitOfflineMode()
        }
    }
}

/** Update credentials for an existing server (e.g., after re-authentication). */
fun updateServerConfig(config: JellyfinServerConfig) {
    if (config.accessToken != null) {
        resetSessionExpiryGuard()
    }
    jellyfinServers.value = jellyfinServers.value.map {
        if (it._id == config._id) config else it
    }
    if (activeServerId.value == config._id.toString()) {
        jellyfinServerConfig.value = config
        // Close the old client before creating a new one
        jellyfinClient.value?.close()
        jellyfinClient.value = if (config.accessToken != null) {
            JellyfinClient(
                serverUrl = config.serverUrl,
                accessToken = config.accessToken,
                userId = config.userId,
                deviceId = config.deviceId
            )
        } else {
            null
        }
        if (config.accessToken != null) {
            ConnectivityState.exitOfflineMode()
            CacheRefresher.start()
        }
    }
}

/** Reinitialize the client from the current active config. */
fun initializeJellyfinClient() {
    // Close the old client to release stale HTTP connections
    jellyfinClient.value?.close()
    val config = jellyfinServerConfig.value
    jellyfinClient.value = if (config != null && config.accessToken != null) {
        JellyfinClient(
            serverUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId,
            deviceId = config.deviceId
        )
    } else {
        null
    }
}

// --- Logout / session expiry ---

private var sessionExpiryHandled = false

private fun resetSessionExpiryGuard() {
    sessionExpiryHandled = false
}

/**
 * Logs out the active server: clears credentials but keeps the server entry so the
 * user only has to re-authenticate rather than re-add the server.
 */
fun logout() {
    val config = jellyfinServerConfig.value ?: return

    // Stop any active playback tied to this session
    PlaybackState.stop()

    // Close the old client to release stale HTTP connections
    jellyfinClient.value?.close()
    jellyfinClient.value = null

    // Clear in-memory API cache and stop background refreshes
    ApiCache.clear()
    CacheRefresher.stop()

    // Keep the server record but drop credentials
    val updated = config.copy(accessToken = null)
    jellyfinServers.value = jellyfinServers.value.map {
        if (it._id == config._id) updated else it
    }
    jellyfinServerConfig.value = updated

    ConnectivityState.exitOfflineMode()
}

/** ID of the server whose session expired and needs re-login. App.kt observes this and routes. */
val sessionExpiredServerId = Signal<String?>(null)

/**
 * Called when a 401 response indicates the session has expired. Logs out and signals
 * the UI to route the user to the login flow for the affected server.
 */
fun handleSessionExpired() {
    if (sessionExpiryHandled) return
    sessionExpiryHandled = true
    val config = jellyfinServerConfig.value ?: return
    if (config.accessToken == null) return

    logout()
    sessionExpiredServerId.value = config._id.toString()
}

// --- Legacy migration ---

private fun migrateLegacyIfNeeded() {
    if (jellyfinServers.value.isNotEmpty()) return // Already migrated

    val legacyConfig = PersistentProperty<JellyfinServerConfig?>("jellyfinServerConfig", null)
    val config = legacyConfig.value ?: return

    // Migrate single server to list
    jellyfinServers.value = listOf(config)
    activeServerId.value = config._id.toString()

    // Migrate flat-key data to scoped keys
    val serverKey = config.storageKey
    migrateListProperty<String>("selectedLibraryIds", "selectedLibraryIds_$serverKey")
}

private inline fun <reified T> migrateListProperty(oldKey: String, newKey: String) {
    val oldProp = PersistentProperty<List<T>>(oldKey, emptyList())
    val oldValue = oldProp.value
    if (oldValue.isNotEmpty()) {
        val newProp = PersistentProperty<List<T>>(newKey, emptyList())
        newProp.value = oldValue
    }
}
