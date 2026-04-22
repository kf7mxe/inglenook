package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.components.gridListView
import com.kf7mxe.inglenook.components.seriesCard
import com.kf7mxe.inglenook.components.SeriesListItem
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.lastItemViewedScrollToOnBack
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending

@Routable("series")
class SeriesPage(val searchQuery: Signal<String> = Signal("")) : Page {
    override val title: Reactive<String> = Constant("Series")

    override fun ViewWriter.render() {
        val allSeries = rememberSuspending {
            ConnectivityState.offlineMode()
            jellyfinClient()?.getAllSeries() ?: emptyList()
        }

        val filteredSeries = remember {
            val query = searchQuery().lowercase()
            if (query.isBlank()) {
                allSeries().sortedBy { it.name.lowercase() }
            } else {
                allSeries().filter { it.name.lowercase().contains(query) }.sortedBy { it.name.lowercase() }
            }
        }

        col {
            paddingByEdge = Edges(1.rem, 0.rem, 1.rem, 0.rem)

            // Search bar and view toggle
            row {
                expanding.fieldTheme.textInput {
                    hint = "Search series..."
                    content bind searchQuery
                }
                viewModeToggleButton()
            }

            // Loading state
            shownWhen { !allSeries.state().ready }.centered.inglenookActivityIndicator()

            // Connection error state
            shownWhen { allSeries().isEmpty() && allSeries.state().ready && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Empty state
            shownWhen { allSeries().isEmpty() && allSeries.state().ready && ConnectivityState.lastNetworkError() == null }.EmptyState(
                icon = Icon.book,
                title = "No series found",
                description = "Books with series metadata will appear here"
            )

            // Series grid/list
            gridListView(
                items = filteredSeries,
                keySelector = { it.id },
                gridItem = { seriesReactive ->
                    seriesCard(seriesReactive) {
                        lastItemViewedScrollToOnBack.set(seriesReactive().id)
                        mainPageNavigator.navigate(SeriesDetailPage(seriesReactive().name))
                    }
                },
                listItem = { seriesReactive ->
                    SeriesListItem(seriesReactive) {
                        lastItemViewedScrollToOnBack.set(seriesReactive().id)
                        mainPageNavigator.navigate(SeriesDetailPage(seriesReactive().name))
                    }
                }
            )
        }
    }
}
