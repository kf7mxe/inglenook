package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.kf7mxe.inglenook.Series
import com.kf7mxe.inglenook.components.SeriesCard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch

@Routable("series")
class SeriesPage : Page {
    override val title: Reactive<String> = Constant("Series")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val allSeries = Signal<List<Series>>(emptyList())
        val searchQuery = Signal("")
        val errorMessage = Signal<String?>(null)

        // Load series
        fun loadSeries() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        allSeries.value = client.getAllSeries()
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load series: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadSeries()

        col {
            // Search bar
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.textField {
                    hint = "Search series..."
                    content bind searchQuery
                }
            }

            // Loading state
            shownWhen { isLoading() }.centered.activityIndicator()

            // Error state
            shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                gap = 0.5.rem
                text { ::content { errorMessage() ?: "" } }
                button {
                    text("Retry")
                    onClick { loadSeries() }
                }
            }

            // Empty state
            shownWhen { allSeries().isEmpty() && !isLoading() && errorMessage() == null }.centered.col {
                gap = 0.5.rem
                text { content = "No series found" }
                subtext { content = "Books with series metadata will appear here" }
            }

            // Series grid with search filtering
            shownWhen { !isLoading() && errorMessage() == null }.expanding.recyclerView {
                ::placer { RecyclerViewPlacerVerticalGrid(2) }

                // Create a reactive filtered list
                val filteredSeries = com.lightningkite.reactive.core.remember {
                    val query = searchQuery().lowercase()
                    if (query.isBlank()) {
                        allSeries()
                    } else {
                        allSeries().filter { it.name.lowercase().contains(query) }
                    }
                }

                children(filteredSeries, { it.id }) { seriesReactive ->
                    SeriesCard(seriesReactive) {
                        mainPageNavigator.navigate(SeriesDetailPage(seriesReactive().name))
                    }
                }
            }
        }
    }
}
