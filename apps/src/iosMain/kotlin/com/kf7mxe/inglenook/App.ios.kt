package com.kf7mxe.inglenook

import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.views.setup
import com.kf7mxe.inglenook.jellyfin.initializeJellyfinClient
import platform.UIKit.UIViewController

fun root(viewController: UIViewController) {
    // Initialize Jellyfin client from stored config
    initializeJellyfinClient()

    viewController.setup(appTheme) {
        app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes })
    }
}
