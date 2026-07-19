@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.LottieAnimations
import com.kf7mxe.inglenook.appTheme
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.activeServerId
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServers
import com.kf7mxe.inglenook.jellyfin.switchToServer
import com.kf7mxe.inglenook.screens.HomePage
import com.lightningkite.kiteui.ExperimentalKiteUi
import com.lightningkite.kiteui.lottie.models.LottieRaw
import com.lightningkite.kiteui.lottie.views.direct.lottie
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalKiteUi::class)
fun ViewWriter.connectivityDialog(dismiss: () -> Unit) {
    val showingServerPicker = Signal(false)

    centered.col {
        shownWhen { !showingServerPicker() }.col {
            sizeConstraints(width = 10.rem, height = 10.rem).lottie(
                source = LottieRaw(LottieAnimations.connectionLost),
                description = "connection lost"
            ) {
                loop = true
                autoPlay = true
                colorTransform = { lottieColor ->
                    if(lottieColor.layerIndex == 2) appTheme.value.background.closestColor()
                    else lottieColor.color
                }
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
                action = Action("Retry Connection") {
                    val success = jellyfinClient.value?.pingServer() ?: false
                    if (success) {
                        ConnectivityState.exitOfflineMode()
                        dismiss()
                    }
                }
            }

            // Switch Server section (only shown when other servers exist)
            val hasOtherServers = jellyfinServers.value.any { it._id.toString() != activeServerId.value }
            shownWhen { hasOtherServers }.col {
                separator()
                val otherServers = jellyfinServers.value.filter { it._id.toString() != activeServerId.value }
                if (otherServers.size == 1) {
                    // Exactly one other server — direct switch button
                    button {
                        centered.text("Switch to ${otherServers[0].displayName}")
                        onClick {
                            switchToServer(otherServers[0]._id.toString())
                            dismiss()
                            mainPageNavigator.navigate(HomePage())
                        }
                        themeChoice += ImportantSemantic
                    }
                } else {
                    // Multiple other servers — open server picker
                    button {
                        centered.text("Switch Server")
                        onClick { showingServerPicker.value = true }
                        themeChoice += ImportantSemantic
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

        // === Server picker view (for 3+ servers) ===
        shownWhen { showingServerPicker() }.col {
            centered.h3 { content = "Switch Server" }

            forEach(remember {
                jellyfinServers.value.filter { it._id.toString() != activeServerId.value }
            }) { server ->
                button {
                    centered.col {
                        text { content = server.displayName }
                        subtext { content = server.serverUrl }
                    }
                    onClick {
                        switchToServer(server._id.toString())
                        dismiss()
                        mainPageNavigator.navigate(HomePage())
                    }
                }
                separator()
            }

            button {
                centered.subtext("Cancel")
                onClick { showingServerPicker.value = false }
            }
        }
    }
}
