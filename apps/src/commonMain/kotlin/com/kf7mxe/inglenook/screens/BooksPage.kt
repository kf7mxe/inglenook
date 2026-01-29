package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch

@Routable("/books")
class BooksPage : Page {
    override val title get() = Constant("Books")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val books = Signal<List<AudioBook>>(emptyList())
        val searchQuery = Signal("")
        val viewMode = Signal(ViewMode.Grid)
        val errorMessage = Signal<String?>(null)

        // Filter books based on search query
        val filteredBooks: Reactive<List<AudioBook>> = remember {
            val query = searchQuery.value.lowercase().trim()
            if (query.isEmpty()) return@remember books.value
            return@remember books.value.filter { book ->
                book.title.lowercase().contains(query) ||
                book.authors.any { it.lowercase().contains(query) } ||
                book.seriesName?.lowercase()?.contains(query) == true
            }
        }

        // Load books
        fun loadBooks() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        books.value = client.getAllBooks()
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load books: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadBooks()

        col {
            gap = 0.rem

            // Search bar and view toggle
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.textField {
                    hint = "Search books..."
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind searchQuery
                }

                // View mode toggle
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

            separator()

            expanding.frame {
                // Loading state
                shownWhen { isLoading() }.centered.activityIndicator()

                // Error state
                shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                    gap = 0.5.rem
                    text { ::content { errorMessage() ?: "" } }
                    button {
                        text("Retry")
                        onClick { loadBooks() }
                    }
                }

                // Content
                shownWhen { !isLoading() && errorMessage() == null }.frame {
                    // Empty state
                    shownWhen { books().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        icon(Icon.book.copy(width = 3.rem, height = 3.rem), "Books")
                        text("No books found")
                        subtext("Your audiobook library is empty")
                    }

                    // No search results
                    shownWhen { books().isNotEmpty() && filteredBooks().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                        text("No results found")
                        subtext { ::content { "No books match \"${searchQuery()}\"" } }
                    }

                    // Books list/grid
                    shownWhen { filteredBooks().isNotEmpty() }.scrolls.col {
                        padding = 1.rem
                        gap = 1.rem

                        // Grid view
                        shownWhen { viewMode() == ViewMode.Grid }.row {
                            gap = 1.rem
                            forEach(filteredBooks ){ book ->
                                BookCard(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                            }
                        }

                        // List view
                        shownWhen { viewMode() == ViewMode.List }.col {
                            gap = 0.5.rem
                            forEach(filteredBooks ) { book ->
                                BookListItem(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                                separator()
                            }
                        }
                    }
                }
            }
        }
    }
}
