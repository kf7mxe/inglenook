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
import com.kf7mxe.inglenook.Bookshelf
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.check
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.edit
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Routable("/bookshelf/{bookshelfId}")
class BookshelfPage(val bookshelfId: String) : Page {
    override val title: Reactive<String> = Constant("Bookshelf")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val bookshelf = Signal<Bookshelf?>(null)
        val books = Signal<List<AudioBook>>(emptyList())
        val viewMode = Signal(ViewMode.Grid)
        val isEditing = Signal(false)
        val errorMessage = Signal<String?>(null)

        // Load bookshelf and its books
        fun loadData() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val uuid = Uuid.parse(bookshelfId)
                    val shelf = BookshelfRepository.getBookshelf(uuid)
                    bookshelf.value = shelf

                    if (shelf != null && shelf.bookIds.isNotEmpty()) {
                        // Fetch book details from Jellyfin
                        val client = jellyfinClient.value
                        if (client != null) {
                            val loadedBooks = shelf.bookIds.mapNotNull { bookId ->
                                try {
                                    client.getBook(bookId)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            books.value = loadedBooks
                        }
                    } else {
                        books.value = emptyList()
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load bookshelf: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Remove book from bookshelf
        fun removeBook(bookId: String) {
            AppScope.launch {
                try {
                    val uuid = Uuid.parse(bookshelfId)
                    BookshelfRepository.removeBookFromBookshelf(uuid, bookId)
                    books.value = books.value.filter { it.id != bookId }
                } catch (e: Exception) {
                    // Ignore error
                }
            }
        }

        // Initial load
        loadData()

        col {
            gap = 0.rem

            // Header with shelf name and actions
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.h2 { ::content { bookshelf()?.name ?: "Bookshelf" } }

                // Edit toggle
                button {
                    icon {
                        ::source { if (isEditing()) Icon.check else Icon.edit }
                        ::description { if (isEditing()) "Done" else "Edit" }
                    }
                    onClick { isEditing.value = !isEditing.value }
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
                        onClick { loadData() }
                    }
                }

                // Content
                shownWhen { !isLoading() && errorMessage() == null }.frame {
                    // Empty state
                    shownWhen { books().isEmpty() }.centered.col {
                        gap = 1.rem
                        icon(Icon.book.copy(width = 3.rem, height = 3.rem), "Books")
                        text("No books in this bookshelf")
                        subtext("Add books from the book detail page")
                    }

                    // Books list/grid
                    shownWhen { books().isNotEmpty() }.scrolls.col {
                        padding = 1.rem
                        gap = 1.rem

                        // Grid view
                        shownWhen { viewMode() == ViewMode.Grid && !isEditing() }.row {
                            gap = 1.rem
                            forEach(books) { book ->
                                BookCard(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                            }
                        }

                        // List view (also used for editing)
                        shownWhen { viewMode() == ViewMode.List || isEditing() }.col {
                            gap = 0.5.rem
                            forEach(books) { book ->
                                row {
                                    gap = 0.5.rem

                                    // Remove button (when editing)
                                    shownWhen { isEditing() }.button {
                                        icon(Icon.remove, "Remove")
                                        onClick {
                                            removeBook(book.id)
                                        }
                                        themeChoice += ThemeDerivation { it.copy(id = "danger", foreground = Color.red).withoutBack }
                                    }

                                    expanding.frame {
                                        BookListItem(book) {
                                            if (!isEditing.value) {
                                                mainPageNavigator.navigate(BookDetailPage(book.id))
                                            }
                                        }
                                    }
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
