@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.jellyfin

import com.kf7mxe.inglenook.JellyfinServerConfig
import com.kf7mxe.inglenook.cache.ApiCache
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
    // Remove any existing config for the same server+user combo to avoid duplicates
    val existing = jellyfinServers.value.filter {
        !(it.serverUrl == config.serverUrl && it.userId == config.userId)
    }
    jellyfinServers.value = existing + config
    switchToServer(config._id.toString())
}

/** Refreshes server capabilities (permissions, plugin support) for the active server config and persists the result. */
fun refreshServerCapabilities(config: JellyfinServerConfig) {
    println("DEBUG config ${config}")
    AppScope.launch {
        try {
            println("DEBUG in refreshServerCpabiltiy? ${ jellyfinClient.invoke() == null}")
            val client = jellyfinClient.invoke() ?: return@launch
            val canEditCollection = client.getCanEditCollection()
            val identifyAvailable = client.isIdentifyAvailable()

            if (canEditCollection != config.canEditCollection || identifyAvailable != config.identifyAvailable) {
                val updated = config.copy(
                    canEditCollection = canEditCollection,
                    identifyAvailable = identifyAvailable
                )
                jellyfinServers.value = jellyfinServers.value.map {
                    if (it._id == updated._id) updated else it
                }
                jellyfinServerConfig.value = updated
            }
        } catch (e: Exception) {
            println("DEBUG refreshServerCapabilities: exception=$e")
        }
    }
}

/** Switch the active server. Stops playback, clears cache, reinitializes client. */
fun switchToServer(serverId: String) {
    val config = jellyfinServers.value.find { it._id.toString() == serverId } ?: return

    // Stop any active playback (it's tied to the old server)
    PlaybackState.stop()

    // Clear in-memory API cache
    ApiCache.clear()

    // Update active server
    activeServerId.value = serverId
    jellyfinServerConfig.value = config

    // Reinitialize client
    jellyfinClient.value = JellyfinClient(
        serverUrl = config.serverUrl,
        accessToken = config.accessToken,
        userId = config.userId,
        deviceId = config.deviceId
    )

    refreshServerCapabilities(config)
}

/** Remove a server from the list. If it's the active server, switch to another or clear. */
fun removeServer(serverId: String) {
    jellyfinServers.value = jellyfinServers.value.filter { it._id.toString() != serverId }

    if (activeServerId.value == serverId) {
        val remaining = jellyfinServers.value
        if (remaining.isNotEmpty()) {
            switchToServer(remaining.first()._id.toString())
        } else {
            activeServerId.value = null
            jellyfinServerConfig.value = null
            jellyfinClient.value = null
        }
    }
}

/** Update credentials for an existing server (e.g., after re-authentication). */
fun updateServerConfig(config: JellyfinServerConfig) {
    jellyfinServers.value = jellyfinServers.value.map {
        if (it._id == config._id) config else it
    }
    if (activeServerId.value == config._id.toString()) {
        jellyfinServerConfig.value = config
        jellyfinClient.value = JellyfinClient(
            serverUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId,
            deviceId = config.deviceId
        )
    }
}

/** Reinitialize the client from the current active config. */
fun initializeJellyfinClient() {
    val config = jellyfinServerConfig.value
    jellyfinClient.value = if (config != null) {
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
