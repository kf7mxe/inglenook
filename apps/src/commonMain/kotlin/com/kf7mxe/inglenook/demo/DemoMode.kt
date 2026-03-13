@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.demo

import com.kf7mxe.inglenook.JellyfinServerConfig
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.lightningkite.reactive.core.Signal
import kotlin.uuid.ExperimentalUuidApi

object DemoMode {
    val isActive = Signal(false)

    fun activate() {
        isActive.value = true
        jellyfinServerConfig.value = JellyfinServerConfig(
            serverUrl = "https://demo.inglenook.app",
            userId = "demo-user",
            username = "Demo User",
            accessToken = "demo-token",
            deviceId = "demo-device",
            serverName = "Inglenook Demo",
        )
        jellyfinClient.value = DemoJellyfinClient()
    }

    fun deactivate() {
        isActive.value = false
        jellyfinServerConfig.value = null
        jellyfinClient.value = null
    }
}
