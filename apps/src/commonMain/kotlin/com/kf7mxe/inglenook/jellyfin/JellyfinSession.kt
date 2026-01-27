package com.kf7mxe.inglenook.jellyfin

import com.kf7mxe.inglenook.JellyfinServerConfig
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember

// Persistent storage for Jellyfin configuration
val jellyfinServerConfig = PersistentProperty<JellyfinServerConfig?>("jellyfinServerConfig", null)

// Selected library ID for filtering books
val selectedLibraryId = PersistentProperty<String?>("selectedLibraryId", null)

// Reactive client instance - created when config is available
val jellyfinClient: Signal<JellyfinClient?> = Signal<JellyfinClient?>(null).also { signal ->
    // Initialize client from stored config
    val config = jellyfinServerConfig.value
    if (config != null) {
        signal.value = JellyfinClient(
            serverUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId,
            deviceId = config.deviceId
        )
    }
}

// Update client when config changes
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
