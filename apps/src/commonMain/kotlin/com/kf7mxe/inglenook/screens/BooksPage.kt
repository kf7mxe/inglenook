package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.applySafeInsets
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

enum class ViewMode { Grid, List }
enum class BooksTab { Books, Bookshelves }

@Serializable
data class BooksPage(val unit: Unit = Unit) : Page {
    override val title: ReactiveContext.() -> String = { "Books" }

    override fun ViewWriter.render() {
        val books = Signal<List<AudioBook>>(emptyList())
        val bookshelves = Signal<List<Bookshelf>>(emptyList())
        val searchQuery = Signal("")
        val viewMode = Signal(ViewMode.Grid)
        val currentTab = Signal(BooksTab.Books)
        val isLoading = Signal(true)

        // Load books when page loads
        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    val allBooks = client.getAllBooks()
                    books.value = allBooks
                }
                // TODO: Load bookshelves from local storage
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

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

                button {
                    icon {
                        ::source { if (viewMode() == ViewMode.Grid) Icon.viewList else Icon.viewModule }
                    }
                    onClick {
                        viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                    }
                }
            }

            // Tab bar
            row {
                padding = Edges(horizontal = 1.rem, vertical = 0.5.rem)
                gap = 0.5.rem

                button {
                    themeChoice = ThemeDerivation {
                        if (currentTab.value == BooksTab.Books) it[SelectedSemantic]
                        else it.withBack
                    }.onNext
                    text("Books")
                    onClick { currentTab.value = BooksTab.Books }
                }

                button {
                    themeChoice = ThemeDerivation {
                        if (currentTab.value == BooksTab.Bookshelves) it[SelectedSemantic]
                        else it.withBack
                    }.onNext
                    text("Bookshelves")
                    onClick { currentTab.value = BooksTab.Bookshelves }
                }
            }

            separator()

            // Content area
            expanding.col {
                shownWhen { isLoading() }.centered.activityIndicator()

                // Books tab content
                shownWhen { !isLoading() && currentTab() == BooksTab.Books }.expanding.col {
                    val filteredBooks = remember {
                        val query = searchQuery().lowercase()
                        if (query.isBlank()) {
                            books()
                        } else {
                            books().filter {
                                it.title.lowercase().contains(query) ||
                                it.authors.any { author -> author.lowercase().contains(query) }
                            }
                        }
                    }

                    shownWhen { filteredBooks().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        text("No books found")
                        shownWhen { searchQuery().isNotBlank() }.subtext("Try a different search term")
                    }

                    shownWhen { filteredBooks().isNotEmpty() && viewMode() == ViewMode.Grid }.scrolls.col {
                        padding = 1.rem

                        // Grid layout using rows of cards
                        reactiveSuspending {
                            clearChildren()
                            val booksToShow = filteredBooks()
                            val chunkedBooks = booksToShow.chunked(3)

                            for (rowBooks in chunkedBooks) {
                                row {
                                    gap = 1.rem
                                    for (book in rowBooks) {
                                        expanding.BookCard(book) {
                                            mainPageNavigator.navigate(BookDetailPage(book.id))
                                        }
                                    }
                                    // Fill empty space if row is incomplete
                                    repeat(3 - rowBooks.size) {
                                        expanding.space(1.0)
                                    }
                                }
                            }
                        }
                    }

                    shownWhen { filteredBooks().isNotEmpty() && viewMode() == ViewMode.List }.scrolls.col {
                        reactiveSuspending {
                            clearChildren()
                            for (book in filteredBooks()) {
                                BookListItem(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                            }
                        }
                    }
                }

                // Bookshelves tab content
                shownWhen { !isLoading() && currentTab() == BooksTab.Bookshelves }.expanding.col {
                    padding = 1.rem
                    gap = 1.rem

                    shownWhen { bookshelves().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        text("No bookshelves yet")
                        subtext("Create a bookshelf to organize your audiobooks")

                        button {
                            row {
                                gap = 0.5.rem
                                icon(Icon.add, "Add")
                                text("Create Bookshelf")
                            }
                            onClick {
                                // TODO: Show create bookshelf dialog
                            }
                            themeChoice = ImportantSemantic.onNext
                        }
                    }

                    shownWhen { bookshelves().isNotEmpty() }.scrolls.col {
                        reactiveSuspending {
                            clearChildren()
                            for (shelf in bookshelves()) {
                                button {
                                    row {
                                        gap = 1.rem
                                        col {
                                            text(shelf.name)
                                            subtext("${shelf.bookIds.size} books")
                                        }
                                    }
                                    onClick {
                                        mainPageNavigator.navigate(BookshelfPage(shelf._id.toString()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
