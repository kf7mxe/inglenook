package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.cloudOff
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch

fun ViewWriter.offlineBanner() {
    shownWhen { ConnectivityState.offlineMode() }.row {
        padding = 0.5.rem
        gap = 0.5.rem
        themeChoice += ThemeDerivation {
            it.copy(
                id = "offline-banner",
                background = Color.fromHexString("#FFA000"),
                foreground = Color.white,
            ).withBack
        }

        centered.icon {
            source = Icon.cloudOff
            description = "Offline"
        }
        expanding.centered.text("Offline Mode")
        button {
            text("Reconnect")
            onClick {
                AppScope.launch {
                    val success = jellyfinClient.value?.pingServer() ?: false
                    if (success) {
                        ConnectivityState.exitOfflineMode()
                    }
                }
            }
        }
    }
}
