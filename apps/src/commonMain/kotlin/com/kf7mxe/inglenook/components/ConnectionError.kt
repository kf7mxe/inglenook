package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.cloudOff
import com.kf7mxe.inglenook.connectionLost
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.ExperimentalKiteUi
import com.lightningkite.kiteui.lottie.models.LottieRaw
import com.lightningkite.kiteui.lottie.views.direct.lottie
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.reactive.context.invoke

/**
 * Reusable "Unable to Connect" error state with Retry and Go Offline buttons.
 * [onRetrySuccess] is called after a successful ping so the caller can refresh/navigate.
 */
@OptIn(ExperimentalKiteUi::class)
fun ViewWriter.connectionError(onRetrySuccess: () -> Unit) {
    centered.col {
        gap = 1.rem
//        centered.icon(Icon.cloudOff.copy(width = 4.rem, height = 4.rem), "No connection")

        sizeConstraints(width = 10.rem, height = 10.rem).lottie(
            source = LottieRaw(connectionLost),
            description = "connection lost"
        ) {
            loop = true
            autoPlay = true
            colorTransform = { lottieColor ->
                println("DEBUG lottieColor: $lottieColor")
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
    }
}
