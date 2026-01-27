package com.kf7mxe.inglenook

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.views.*
import com.kf7mxe.inglenook.jellyfin.initializeJellyfinClient

fun main() {
    // Initialize Jellyfin client from stored config
    initializeJellyfinClient()

    root(appTheme.value) {
        app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes })
    }
}
