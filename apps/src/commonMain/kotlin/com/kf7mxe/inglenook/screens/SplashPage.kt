package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.FullScreen
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Routable("/splash")
class SplashPage : Page, FullScreen {
    override val title get() = Constant("Loading")

    override fun ViewWriter.render() {
        centered.col {
            activityIndicator { }
        }

        AppScope.launch {
            val client = jellyfinClient.value
            if (client == null) {
                mainPageNavigator.navigate(HomePage())
                return@launch
            }

            // Start background warmup immediately (non-blocking, survives navigation)


            // Warm critical HomePage data with 30s timeout
            withTimeoutOrNull(30_000L) {
                val inProgress = async {
                    try { client.getInProgressBooks() } catch (_: Exception) { emptyList() }
                }
                val recommended = async {
                    try { client.getSuggestedBooks() } catch (_: Exception) { emptyList() }
                }
                val recentlyAdded = async {
                    try { client.getRecentlyAddedBooks() } catch (_: Exception) { emptyList() }
                }
                AppScope.launch {
                    try { client.getAllBooks() } catch (_: Exception) { /* best effort */ }
                }
                inProgress.await()
                recommended.await()
                recentlyAdded.await()
            }


            mainPageNavigator.navigate(HomePage())
            AppScope.launch {
                try { client.getAuthors() } catch (_: Exception) { /* best effort */ }
            }
        }
    }
}
