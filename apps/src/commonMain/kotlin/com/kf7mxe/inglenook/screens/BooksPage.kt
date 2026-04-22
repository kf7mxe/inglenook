package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.bookCard
import com.kf7mxe.inglenook.components.bookListItem
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.components.gridListView
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.lastItemViewedScrollToOnBack
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
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
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
            ConnectivityState.offlineMode()
            jellyfinClient()?.getAllBooks() ?: emptyList()
        }

        val filteredBooks: Reactive<List<Book>> = remember {
            val query = searchQuery().lowercase().trim()
            val filter = selectedFilter()
            var result = books()

            if (query.isNotEmpty()) {
                result = result.filter { book ->
                    book.title.lowercase().contains(query) ||
                            book.authors.any { it.name.lowercase().contains(query) } ||
                            book.seriesName?.lowercase()?.contains(query) == true
                }
            }

            if (filter != null) {
                result = result.filter { filter.filterFn(it) }
            }

            val typeFilter = bookTypeFilter()
            if (typeFilter != null) {
                result = result.filter { it.itemType == typeFilter }
            }

            result.sortedBy { it.title.lowercase() }
        }

        col {
            paddingByEdge = Edges(1.rem, 0.rem, 1.rem, 0.rem)

            reactive{
                println("DEBUG lastLookedAtBook ${lastItemViewedScrollToOnBack()}")
            }

            // Search bar and view toggle
            row {
                expanding.fieldTheme.textInput {
                    hint = "Search books..."
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind searchQuery
                }

                // Book type filter toggles
                card.button {
                    text("All")
                    onClick { bookTypeFilter.value = null }
                    dynamicTheme { if (bookTypeFilter() == null) ImportantSemantic else null }
                }
                card.button {
                    text("Audio")
                    onClick { bookTypeFilter.value = ItemType.AudioBook }
                    dynamicTheme { if (bookTypeFilter() == ItemType.AudioBook) ImportantSemantic else null }
                }
                card.button {
                    text("Ebooks")
                    onClick { bookTypeFilter.value = ItemType.Ebook }
                    dynamicTheme { if (bookTypeFilter() == ItemType.Ebook) ImportantSemantic else null }
                }

                viewModeToggleButton()
            }

            shownWhen { !books.state().ready }.inglenookActivityIndicator()

            // Connection error state
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Empty / no results states
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() == null }.frame {
                shownWhen { books().isEmpty() }.EmptyState(
                    icon = Icon.book,
                    title = "No books found",
                    description = "Your audiobook library is empty"
                )

                shownWhen { books().isNotEmpty() && filteredBooks().isEmpty() }.centered.col {
                    gap = 0.5.rem
                    icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                    text("No results found")
                    subtext { ::content { "No books match your search" } }
                }
            }

            // Books grid/list
            gridListView(
                items = filteredBooks,
                keySelector = { it.id },
                gridItem = { book ->
                    bookCard(book) {
                        lastItemViewedScrollToOnBack.set(book().id)
                        mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                    }
                },
                listItem = { book ->
                    bookListItem(book) {
                        lastItemViewedScrollToOnBack.set(book().id)
                        mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                    }
                }
            )
        }
    }
}
