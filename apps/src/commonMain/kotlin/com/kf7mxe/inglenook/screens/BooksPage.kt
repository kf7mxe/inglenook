package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.dynamicTheme
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.Bookshelf
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.collectionsBookmark
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

enum class BooksTab { Books, Bookshelves }

data class FilterOption(val id: String, val label: String, val filterFn: (AudioBook) -> Boolean)

@Routable("/books")
class BooksPage : Page {
    override val title get() = Constant("Books")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        val currentTab = Signal(BooksTab.Books)
        val isLoading = Signal(true)
        val books = Signal<List<AudioBook>>(emptyList())
        val bookshelves = Signal<List<Bookshelf>>(emptyList())
        val searchQuery = Signal("")
        val viewMode = Signal(ViewMode.Grid)
        val errorMessage = Signal<String?>(null)
        val selectedFilter = Signal<FilterOption?>(null)
        val showCreateDialog = Signal(false)
        val newBookshelfName = Signal("")

        // Get unique series for filter options
        val seriesFilters: Reactive<List<FilterOption>> = remember {
            val allBooks = books()
            val series = allBooks.mapNotNull { it.seriesName }.distinct().sorted()
            series.map { seriesName ->
                FilterOption(
                    id = "series-$seriesName",
                    label = seriesName,
                    filterFn = { book -> book.seriesName == seriesName }
                )
            }
        }

        // Filter books based on search query and selected filter
        val filteredBooks: Reactive<List<AudioBook>> = remember {
            val query = searchQuery().lowercase().trim()
            val filter = selectedFilter()
            var result = books()

            // Apply search filter
            if (query.isNotEmpty()) {
                result = result.filter { book ->
                    book.title.lowercase().contains(query) ||
                    book.authors.any { it.lowercase().contains(query) } ||
                    book.seriesName?.lowercase()?.contains(query) == true
                }
            }

            // Apply selected filter
            if (filter != null) {
                result = result.filter { filter.filterFn(it) }
            }

            result
        }

        // Load books and bookshelves
        fun loadData() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        books.value = client.getAllBooks()
                    }
                    bookshelves.value = BookshelfRepository.getAllBookshelves()
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load data: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Create a new bookshelf
        fun createBookshelf() {
            val name = newBookshelfName.value.trim()
            if (name.isNotEmpty()) {
                BookshelfRepository.createBookshelf(name)
                bookshelves.value = BookshelfRepository.getAllBookshelves()
                newBookshelfName.value = ""
                showCreateDialog.value = false
            }
        }

        // Initial load
        loadData()

        col {
            gap = 0.rem

            // Tab buttons
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.button {
                    text("Books")
                    onClick { currentTab.value = BooksTab.Books }
                    dynamicTheme {
                        if (currentTab.value == BooksTab.Books) ImportantSemantic else null
                    }
                }

                expanding.button {
                    text("Bookshelves")
                    onClick { currentTab.value = BooksTab.Bookshelves }
                    dynamicTheme {
                        if (currentTab.value == BooksTab.Bookshelves) ImportantSemantic else null
                    }
                }
            }

            separator()

            // Books Tab Content
            shownWhen { currentTab() == BooksTab.Books }.expanding.col {
                gap = 0.rem

                // Search bar and view toggle
                row {
                    padding = 1.rem
                    gap = 0.5.rem

                    expanding.textInput {
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

                // Filter chips (series filters)
                shownWhen { seriesFilters().isNotEmpty() }.scrollingHorizontally.row {
                    padding = 1.rem
                    gap = 0.5.rem

                    // "All" chip
                    button {
                        text("All")
                        onClick { selectedFilter.value = null }
                        dynamicTheme {
                            if (selectedFilter.value == null) ImportantSemantic else null
                        }
                    }

                    // Series filter chips
                    forEach(seriesFilters) { filter ->
                        button {
                            text(filter.label)
                            onClick {
                                selectedFilter.value = if (selectedFilter.value?.id == filter.id) null else filter
                            }
                            dynamicTheme {
                                if (selectedFilter.value?.id == filter.id) ImportantSemantic else null
                            }
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
                            onClick { loadData() }
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
                            subtext { ::content { "No books match your search" } }
                        }

                        // Books list/grid
                        shownWhen { filteredBooks().isNotEmpty() }.scrolling.col {
                            padding = 1.rem
                            gap = 1.rem

                            // Grid view
                            shownWhen { viewMode() == ViewMode.Grid }.row {
                                gap = 1.rem
                                forEach(filteredBooks) { book ->
                                    BookCard(book) {
                                        mainPageNavigator.navigate(BookDetailPage(book.id))
                                    }
                                }
                            }

                            // List view
                            shownWhen { viewMode() == ViewMode.List }.col {
                                gap = 0.5.rem
                                forEach(filteredBooks) { book ->
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

            // Bookshelves Tab Content
            shownWhen { currentTab() == BooksTab.Bookshelves }.expanding.col {
                gap = 0.rem

                // Header with create button
                row {
                    padding = 1.rem
                    gap = 0.5.rem

                    expanding.h3 { content = "Your Bookshelves" }

                    button {
                        row {
                            gap = 0.25.rem
                            icon(Icon.add, "Add")
                            text("Create")
                        }
                        onClick { showCreateDialog.value = true }
                        themeChoice += ImportantSemantic
                    }
                }

                separator()

                expanding.frame {
                    // Loading state
                    shownWhen { isLoading() }.centered.activityIndicator()

                    // Empty state
                    shownWhen { !isLoading() && bookshelves().isEmpty() }.centered.col {
                        gap = 1.rem
                        padding = 2.rem

                        icon(Icon.collectionsBookmark.copy(width = 4.rem, height = 4.rem), "Bookshelves")
                        h3 { content = "No Bookshelves Yet" }
                        text { content = "Create a bookshelf to organize your audiobooks" }
                        button {
                            row {
                                gap = 0.25.rem
                                icon(Icon.add, "Add")
                                text("Create Bookshelf")
                            }
                            onClick { showCreateDialog.value = true }
                            themeChoice += ImportantSemantic
                        }
                    }

                    // Bookshelves grid
                    shownWhen { !isLoading() && bookshelves().isNotEmpty() }.scrolling.col {
                        padding = 1.rem
                        gap = 1.rem

                        row {
                            gap = 1.rem
                            forEach(bookshelves) { bookshelf ->
                                button {
                                    col {
                                        gap = 0.5.rem

                                        // Bookshelf icon
                                        sizedBox(SizeConstraints(width = 8.rem, height = 8.rem)).centered.frame {
                                            icon(Icon.collectionsBookmark.copy(width = 4.rem, height = 4.rem), bookshelf.name)
                                        }

                                        // Name and count
                                        col {
                                            gap = 0.rem
                                            text {
                                                content = bookshelf.name
                                                ellipsis = true
                                            }
                                            subtext {
                                                content = "${bookshelf.bookIds.size} books"
                                            }
                                        }
                                    }
                                    onClick {
                                        mainPageNavigator.navigate(BookshelfPage(bookshelf._id.toString()))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create Bookshelf Dialog (simple overlay)
            shownWhen { showCreateDialog() }.centered.col {
                padding = 2.rem
                gap = 1.rem

                card.col {
                    gap = 1.rem
                    padding = 1.rem

                    h3 { content = "Create Bookshelf" }

                    col {
                        gap = 0.25.rem
                        text("Name")
                        textInput {
                            hint = "My Bookshelf"
                            content bind newBookshelfName
                        }
                    }

                    row {
                        gap = 0.5.rem
                        expanding.button {
                            text("Cancel")
                            onClick {
                                showCreateDialog.value = false
                                newBookshelfName.value = ""
                            }
                        }
                        expanding.button {
                            text("Create")
                            onClick { createBookshelf() }
                            themeChoice += ImportantSemantic
                        }
                    }
                }
            }
        }
    }
}
