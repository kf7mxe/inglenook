package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.FullScreen
import com.kf7mxe.inglenook.Resources
import com.kf7mxe.inglenook.animatePulsating
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.reactive.core.AppScope
import com.kf7mxe.inglenook.cache.CacheRefresher
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.bold
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

@Routable("/splash")
class SplashPage : Page, FullScreen {
    override val title get() = Constant("Loading")

    override fun ViewWriter.render() {

        val currentlyLoading = Signal("Lighting the fire...")

        AppScope.launch {
            val client = jellyfinClient.value
            if (client == null) {
                mainPageNavigator.reset(HomePage())
                return@launch
            }

            // Start background warmup immediately (non-blocking, survives navigation)


            // Warm critical HomePage data with 30s timeout
            withTimeoutOrNull(15_000L.milliseconds) {
                // 1. Fire off all network requests in parallel
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

                // 2. Await them sequentially with a guaranteed minimum display time
                val minDisplayTime = 800L // Adjust this to make it read faster/slower

                currentlyLoading.set("Finding your bookmarks...")
                val timer1 = async { delay(minDisplayTime) } // Start a timer
                inProgress.await() // Wait for the network
                timer1.await() // Ensure at least 800ms has passed before moving on

                currentlyLoading.set("Gathering some cozy recommendations...")
                val timer2 = async { delay(minDisplayTime) }
                recommended.await()
                timer2.await()

                currentlyLoading.set("  Stacking the newest arrivals...")
                val timer3 = async { delay(minDisplayTime) }
                recentlyAdded.await()
                timer3.await()
            }


            CacheRefresher.start()
            mainPageNavigator.reset(HomePage())
            AppScope.launch {
                try { client.getAuthors() } catch (_: Exception) { /* best effort */ }
            }
        }
        centered.frame {
            val animation = rememberSuspending {
                ImageRaw(Resources.inglenookFlameAnimation())
            }
            centered.col {
                sizeConstraints(width = 10.rem, height = 10.rem).image {
                    ::source {
                        animation()
                    }
                }
                animatePulsating()
                bold.subtext {
                    ::content {
                        currentlyLoading()
                    }
                }
            }
        }
    }
}
