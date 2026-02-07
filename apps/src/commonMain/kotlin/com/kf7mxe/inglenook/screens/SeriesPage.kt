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
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch

@Routable("series")
class SeriesPage : Page {
    override val title: Reactive<String> = Constant("Series")

    override fun ViewWriter.render() {
        val allSeries = rememberSuspending {
            val client = jellyfinClient.value
            val test = client?.getAllSeries()?:emptyList()
            println("DEBUG test ${test.size}")
            test
        }
        val searchQuery = Signal("")
        val errorMessage = Signal<String?>(null)


        col {
            // Search bar
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.textInput {
                    hint = "Search series..."
                    content bind searchQuery
                }
            }

            // Loading state
            shownWhen { !allSeries.state().ready }.centered.activityIndicator()

            // Error state
//            shownWhen { errorMessage() != null && !isLoading() }.centered.col {
//                gap = 0.5.rem
//                text { ::content { errorMessage() ?: "" } }
//                button {
//                    text("Retry")
//                    onClick { loadSeries() }
//                }
//            }

            // Empty state
            shownWhen { allSeries().isEmpty() && allSeries.state().ready && errorMessage() == null }.centered.col {
                gap = 0.5.rem
                text { content = "No series found" }
                subtext { content = "Books with series metadata will appear here" }
            }

            // Series grid with search filtering
            shownWhen { allSeries.state().ready && errorMessage() == null }.recyclerView {
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

                children(allSeries, { it.id }) { seriesReactive ->
                    SeriesCard(seriesReactive) {
                        mainPageNavigator.navigate(SeriesDetailPage(seriesReactive().name))
                    }
                }
            }
        }
    }
}
