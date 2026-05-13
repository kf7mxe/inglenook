package com.kf7mxe.inglenook.demo

import kotlinx.browser.window

actual fun isDemoWebsite(): Boolean {
    val hostname = window.location.hostname
    return hostname.contains("kf7mxe.github.io")
}
