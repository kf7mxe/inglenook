package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.models.Edges
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImportantSemantic
import com.lightningkite.kiteui.models.KeyboardCase
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.KeyboardType
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.shownWhen
import com.lightningkite.kiteui.views.direct.textInput
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlin.uuid.ExperimentalUuidApi


class BooksPage(
    val searchQuery: Signal<String> = Signal(""),
    val selectedFilter: Signal<FilterOption?> = Signal(null),
    val bookTypeFilter: Signal<ItemType?> = Signal(null)
) : Page {
    override val title get() = Constant("Books")


    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {


        val books = rememberSuspending {
            ConnectivityState.offlineMode() // Reactive dependency — reloads when connectivity changes
            jellyfinClient()?.getAllBooks() ?: emptyList()
        }


        // Filter books based on search query and selected filter
        val filteredBooks: Reactive<List<Book>> = remember {
            val query = searchQuery().lowercase().trim()
            val filter = selectedFilter()
            var result = books()

            // Apply search filter
            if (query.isNotEmpty()) {
                result = result.filter { book ->
                    book.title.lowercase().contains(query) ||
                            book.authors.any { it.name.lowercase().contains(query) } ||
                            book.seriesName?.lowercase()?.contains(query) == true
                }
            }

            // Apply selected filter
            if (filter != null) {
                result = result.filter { filter.filterFn(it) }
            }

            // Apply book type filter
            val typeFilter = bookTypeFilter()
            if (typeFilter != null) {
                result = result.filter { it.itemType == typeFilter }
            }

            result
        }

        col {
            paddingByEdge = Edges(1.rem, 0.rem, 1.rem, 0.rem)

// Search bar and view toggle
            row {
                expanding.fieldTheme.textInput {
                    hint = "Search books..."
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind searchQuery
                }

                // Book type filter toggles
                buttonTheme.button {
                    text("All")
                    onClick { bookTypeFilter.value = null }
                    dynamicTheme { if (bookTypeFilter() == null) ImportantSemantic else null }
                }
                buttonTheme.button {
                    text("Audio")
                    onClick { bookTypeFilter.value = ItemType.AudioBook }
                    dynamicTheme { if (bookTypeFilter() == ItemType.AudioBook) ImportantSemantic else null }
                }
                buttonTheme.button {
                    text("Ebooks")
                    onClick { bookTypeFilter.value = ItemType.Ebook }
                    dynamicTheme { if (bookTypeFilter() == ItemType.Ebook) ImportantSemantic else null }
                }

                // View mode toggle
                buttonTheme.button {
                    icon {
                        ::source { if (viewMode() == ViewMode.Grid) Icon.menu else Icon.dashboard }
                        description = "Toggle view"
                    }
                    onClick {
                        viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                    }
                }
            }
            shownWhen { !books.state().ready }.centered.activityIndicator()

            // Loading state

            // Error state
//            shownWhen { errorMessage() != null && !isLoading() }.centered.col {
//                gap = 0.5.rem
//                text { ::content { errorMessage() ?: "" } }
//                button {
//                    text("Retry")
//                    onClick { loadData() }
//                }
//            }

            // Connection error state
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Content
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() == null }.frame {
                // Empty state
                shownWhen { books().isEmpty() }.centered.col {
                    icon(Icon.book.copy(width = 3.rem, height = 3.rem), "Books")
                    text("No books found")
                    subtext("Your audiobook library is empty")
                }

                // No search results
                shownWhen { books().isNotEmpty() && filteredBooks().isEmpty() }.centered.col {
                    gap = 0.5.rem
                    icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                    text("No results found")
                    subtext { ::content { "No books match your search" } }
                }
            }

            // Books list/grid
//                    col {
            expanding.swapView {
                swapping(
                    current = {
                        viewMode()
                    },
                    views = { viewMode ->
                        when (viewMode) {
                            ViewMode.Grid -> {
                                expanding.recyclerView {
                                    ::placer{ RecyclerViewPlacerVerticalGrid(2) }
                                    children(filteredBooks, { it.id }) { book ->
                                        BookCard(book) {
                                            mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                        }
                                    }

                                }
                            }

                            ViewMode.List -> {
                                expanding.recyclerView {
                                    children(filteredBooks, { it.id }) { book ->
                                        BookListItem(book) {
                                            mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                        }
                                    }
                                }
                            }
                        }
                    })

            }
        }
    }
}