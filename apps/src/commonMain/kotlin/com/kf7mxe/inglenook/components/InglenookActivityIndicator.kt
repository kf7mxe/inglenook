package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.bookLoading
import com.lightningkite.kiteui.lottie.models.LottieRaw
import com.lightningkite.kiteui.lottie.views.direct.lottie
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.sizeConstraints

fun ViewWriter.inglenookActivityIndicator() {
    frame {
        sizeConstraints(width = 10.rem, height = 10.rem).centered.lottie(
            source = LottieRaw(bookLoading),
            description = "test"
        ) {
            loop = true
            autoPlay = true
        }
    }
}