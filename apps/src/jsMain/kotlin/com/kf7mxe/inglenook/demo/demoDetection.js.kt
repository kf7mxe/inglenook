package com.kf7mxe.inglenook.demo

import kotlinx.browser.window

actual fun isDemoWebsite(): Boolean {
    val hostname = window.location.hostname
    println("DEBUG window.location.hostname: ${window.location.hostname}")
    println("DEBUG HOSTNAME: $hostname")
    println("DEBUG hostname.contains(\"https://kf7mxe.github.io/inglenook ${hostname.contains("https://kf7mxe.github.io/inglenook")}")
    return hostname.contains("https://kf7mxe.github.io/inglenook/")
}
