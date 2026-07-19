@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.LottieAnimations
import com.kf7mxe.inglenook.cloudOff
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
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.remember
import kotlin.uuid.ExperimentalUuidApi

/**
 * Reusable "Unable to Connect" error state with Retry and Go Offline buttons.
 * [onRetrySuccess] is called after a successful ping so the caller can refresh/navigate.
 */
@OptIn(ExperimentalKiteUi::class)
fun ViewWriter.connectionError(onRetrySuccess: () -> Unit) {
    val showingServerPicker = Signal(false)

    centered.col {
        gap = 1.rem

        // === Default view ===
        shownWhen { !showingServerPicker() }.col {
            gap = 1.rem

            sizeConstraints(width = 10.rem, height = 10.rem).lottie(
                source = LottieRaw(LottieAnimations.connectionLost),
                description = "connection lost"
            ) {
                loop = true
                autoPlay = true
                colorTransform = { lottieColor ->
                    if(lottieColor.layerIndex == 2) theme.background.closestColor()
                    else lottieColor.color
                }
            }
            centered.h3 { content = "Unable to Connect" }
            centered.text { content = "Could not reach the Jellyfin server." }
            centered.subtext {
                ::content { ConnectivityState.lastNetworkError() ?: "" }
            }
            centered.row {
                gap = 1.rem
                button {
                    text("Retry")
                    action = Action("Retry") {
                        val success = jellyfinClient.value?.pingServer() ?: false
                        if (success) {
                            ConnectivityState.exitOfflineMode()
                            onRetrySuccess()
                        }
                    }
                    themeChoice += ImportantSemantic
                }
                button {
                    text("Go Offline")
                    onClick {
                        ConnectivityState.enterOfflineMode()
                    }
                }
            }

            // Switch Server section (only shown when other servers exist)
            val hasOtherServers = jellyfinServers.value.any { it._id.toString() != activeServerId.value }
            shownWhen { hasOtherServers }.col {
                gap = 1.rem
                separator()
                val otherServers = jellyfinServers.value.filter { it._id.toString() != activeServerId.value }
                if (otherServers.size == 1) {
                    centered.button {
                        centered.text("Switch to ${otherServers[0].displayName}")
                        onClick {
                            switchToServer(otherServers[0]._id.toString())
                            mainPageNavigator.navigate(HomePage())
                            onRetrySuccess()
                        }
                        themeChoice += ImportantSemantic
                    }
                } else {
                    centered.button {
                        centered.text("Switch Server")
                        onClick { showingServerPicker.value = true }
                        themeChoice += ImportantSemantic
                    }
                }
            }
        }

        // === Server picker view (for 3+ servers) ===
        shownWhen { showingServerPicker() }.col {
            gap = 1.rem

            centered.h3 { content = "Switch Server" }

            forEach(remember {
                jellyfinServers.value.filter { it._id.toString() != activeServerId.value }
            }) { server ->
                centered.button {
                    centered.col {
                        text { content = server.displayName }
                        subtext { content = server.serverUrl }
                    }
                    onClick {
                        switchToServer(server._id.toString())
                        mainPageNavigator.navigate(HomePage())
                        onRetrySuccess()
                    }
                }
                separator()
            }

            centered.button {
                centered.text("Cancel")
                onClick { showingServerPicker.value = false }
            }
        }
    }
}
