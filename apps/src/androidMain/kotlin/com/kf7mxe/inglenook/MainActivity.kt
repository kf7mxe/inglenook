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
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.kotlin.multiplatform.Sentry

class MainActivity : KiteUiActivity() {
    companion object {
        val main = PageNavigator { AutoRoutes }
        val dialog = PageNavigator { AutoRoutes }
    }

    override val theme: ReactiveContext.() -> Theme
        get() = { appTheme() }

    override val mainNavigator: PageNavigator get() = main
    val dialogNavigator: PageNavigator get() = dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        codeCacheDir.setReadOnly()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        Throwable_report = { ex, ctx ->
            ex.printStackTrace2()
        }
        if(diagnosticsEnabled.value) {
            try {
                SentryAndroid.init(this) { options ->
                    // REPLACE THIS WITH YOUR ACTUAL DSN
                    options.dsn = "https://bff986944dbc4e86a20beff428217965@sentry.bagleysclearing.net/2"

                    // Enable heavy debugging
                    options.isDebug = true
                    options.setDiagnosticLevel(SentryLevel.DEBUG)
                    options.isEnableAutoSessionTracking = true
                }
                println("SENTRY: Initialization attempted successfully")
            } catch (e: Exception) {
                println("SENTRY: Initialization CRASHED: ${e.message}")
                e.printStackTrace()
            }
            Sentry.captureMessage("Test")

            Throwable_report = { ex, ctx ->
                ex.printStackTrace2()
                Sentry.captureException(ex)
            }
        }




        // Initialize Jellyfin client from stored config
        initializeJellyfinClient()




        val callback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                // Handle the back button event
                // Example: Show a confirmation dialog

                if(dialogNavigator.canGoBack.state.raw) dialogNavigator.goBack()
                else mainNavigator.goBack()
            }
        }

        // Add the callback to the dispatcher
        onBackPressedDispatcher.addCallback(this, callback)
        viewWriter.app(main, dialog)

    }
}
