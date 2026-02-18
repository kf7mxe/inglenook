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
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.components.SeriesCard
import com.kf7mxe.inglenook.components.SeriesListItem
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch

@Routable("series")
class SeriesPage(val searchQuery:Signal<String> = Signal("")) : Page {
    override val title: Reactive<String> = Constant("Series")

    override fun ViewWriter.render() {
        val allSeries = rememberSuspending {
            val client = jellyfinClient.value
            client?.getAllSeries() ?: emptyList()
        }
        val errorMessage = Signal<String?>(null)


        col {
            paddingByEdge = Edges(1.rem,0.rem,1.rem,0.rem)
            // Search bar

            row {
                expanding.fieldTheme.textInput {
                    hint = "Search series..."
                    content bind searchQuery
                }
                button {
                    icon {
                        ::source { if (viewMode() == ViewMode.Grid) Icon.menu else Icon.dashboard }
                        description = "Toggle view"
                    }
                    onClick {
                        viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                    }
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



            // Connection error state
            shownWhen { allSeries().isEmpty() && allSeries.state().ready && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            shownWhen { allSeries().isEmpty() && allSeries.state().ready && ConnectivityState.lastNetworkError() == null }.centered.col {
                text { content = "No series found" }
                subtext { content = "Books with series metadata will appear here" }
            }


            val filteredSeries = com.lightningkite.reactive.core.remember {
                val query = searchQuery().lowercase()
                if (query.isBlank()) {
                    allSeries()
                } else {
                    allSeries().filter { it.name.lowercase().contains(query) }
                }
            }




            expanding.swapView {
                swapping(current = {
                    viewMode()
                },
                    views = {viewMode ->
                        when(viewMode) {
                            ViewMode.Grid -> {
                                expanding.recyclerView {
                                    ::placer { RecyclerViewPlacerVerticalGrid(2) }

                                    // Create a reactive filtered list


                                    children(allSeries, { it.id }) { seriesReactive ->
                                        SeriesCard(seriesReactive) {
                                            mainPageNavigator.navigate(SeriesDetailPage(seriesReactive().name))
                                        }
                                    }
                                }
                            }
                            ViewMode.List -> {
                                expanding.recyclerView {
                                    children(allSeries, { it.id }) { seriesReactive ->
                                        SeriesListItem(seriesReactive) {
                                            mainPageNavigator.navigate(SeriesDetailPage(seriesReactive().name))
                                        }
                                    }
                                }
                            }
                        }
                    })
            }


            // Empty state


            // Series grid with search filtering

        }
    }
}
