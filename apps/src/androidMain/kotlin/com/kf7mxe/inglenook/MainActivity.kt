package com.kf7mxe.inglenook

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import com.lightningkite.kiteui.KiteUiActivity
import com.lightningkite.kiteui.Throwable_report
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.reactive.context.*
import com.kf7mxe.inglenook.jellyfin.initializeJellyfinClient
import com.kf7mxe.inglenook.screens.AutoRoutes

class MainActivity : KiteUiActivity() {
    companion object {
        val main = PageNavigator { AutoRoutes }
        val dialog = PageNavigator { AutoRoutes }
    }

    override val theme: ReactiveContext.() -> Theme
        get() = { appTheme() }

    override val mainNavigator: PageNavigator get() = main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        codeCacheDir.setReadOnly()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        Throwable_report = { ex, ctx ->
            ex.printStackTrace2()
        }

        // Initialize Jellyfin client from stored config
        initializeJellyfinClient()




        val callback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                // Handle the back button event
                // Example: Show a confirmation dialog
                mainNavigator.goBack()
            }
        }

        // Add the callback to the dispatcher
        onBackPressedDispatcher.addCallback(this, callback)
        viewWriter.app(main, dialog)

    }
}
