package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.cloudOff
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch

fun ViewWriter.connectivityDialog(dismiss: () -> Unit) {
    centered.col {
        centered.icon {
            source = Icon.cloudOff.copy(width = 3.rem, height = 3.rem)
            description = "No connection"
        }
        centered.h3 { content = "Connection Lost" }
        centered.text {
            content = "Unable to reach the Jellyfin server."
        }
        centered.text {
            content = "You can continue in offline mode with your downloaded books."
        }
        centered.subtext {
            ::content {
                ConnectivityState.lastNetworkError() ?: ""
            }
        }

        button {
            centered.row {
                icon(Icon.download, "Offline")
                text("Go Offline")
            }
            onClick {
                ConnectivityState.enterOfflineMode()
                dismiss()
            }
            themeChoice += ImportantSemantic
        }

        button {
            centered.text("Retry Connection")
            onClick {
                AppScope.launch {
                    val success = jellyfinClient.value?.pingServer() ?: false
                    if (success) {
                        ConnectivityState.exitOfflineMode()
                        dismiss()
                    }
                }
            }
        }

        button {
            centered.subtext("Dismiss")
            onClick {
                ConnectivityState.dismissDialog()
                dismiss()
            }
        }
    }
}
