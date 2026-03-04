package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.LottieAnimations
import com.kf7mxe.inglenook.appTheme
import com.kf7mxe.inglenook.cloudOff
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.ExperimentalKiteUi
import com.lightningkite.kiteui.lottie.models.LottieRaw
import com.lightningkite.kiteui.lottie.views.direct.lottie
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.reactive.context.invoke

@OptIn(ExperimentalKiteUi::class)
fun ViewWriter.connectivityDialog(dismiss: () -> Unit) {
    themed(DialogSemantic).centered.col {
//        centered.icon {
//            source = Icon.cloudOff.copy(width = 3.rem, height = 3.rem)
//            description = "No connection"
//        }
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

        button {
            centered.subtext("Dismiss")
            onClick {
                ConnectivityState.dismissDialog()
                dismiss()
            }
        }
    }
}
