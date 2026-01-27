package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class BookshelfPage(val bookshelfId: String) : Page {
    override val title: ReactiveContext.() -> String = { "" }

    override fun ViewWriter.render() {
        val bookshelf = Signal<Bookshelf?>(null)
        val books = Signal<List<AudioBook>>(emptyList())
        val viewMode = Signal(ViewMode.Grid)
        val editMode = Signal(false)
        val isLoading = Signal(true)

        // Load bookshelf and its books
        AppScope.launch {
            try {
                val uuid = Uuid.parse(bookshelfId)
                bookshelf.value = BookshelfRepository.getBookshelf(uuid)

                val client = jellyfinClient.value
                val shelf = bookshelf.value
                if (client != null && shelf != null) {
                    val bookList = mutableListOf<AudioBook>()
                    for (bookId in shelf.bookIds) {
                        try {
                            val book = client.getBook(bookId)
                            if (book != null) {
                                bookList.add(book)
                            }
                        } catch (e: Exception) {
                            // Skip books that can't be loaded
                        }
                    }
                    books.value = bookList
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

        col {
            gap = 0.rem

            // Header with title and actions
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.h2 {
                    ::content { bookshelf()?.name ?: "Bookshelf" }
                }

                button {
                    icon {
                        ::source { if (editMode()) Icon.check else Icon.edit }
                    }
                    onClick { editMode.value = !editMode.value }
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

            separator()

            // Content
            expanding.col {
                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() && bookshelf() == null }.centered.col {
                    gap = 0.5.rem
                    text("Bookshelf not found")
                    button {
                        text("Go Back")
                        onClick { mainPageNavigator.goBack() }
                    }
                }

                shownWhen { !isLoading() && bookshelf() != null }.expanding.col {
                    shownWhen { books().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        text("No books in this bookshelf")
                        button {
                            row {
                                gap = 0.5.rem
                                icon(Icon.add, "Add")
                                text("Add Books")
                            }
                            onClick {
                                // TODO: Show add books dialog
                            }
                            themeChoice = ImportantSemantic.onNext
                        }
                    }

                    shownWhen { books().isNotEmpty() && viewMode() == ViewMode.Grid }.scrolls.col {
                        padding = 1.rem

                        reactiveSuspending {
                            clearChildren()
                            val chunked = books().chunked(3)
                            for (rowBooks in chunked) {
                                row {
                                    gap = 1.rem
                                    for (book in rowBooks) {
                                        expanding.col {
                                            BookCard(book) {
                                                if (!editMode.value) {
                                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                                }
                                            }
                                            shownWhen { editMode() }.button {
                                                centered.icon(Icon.delete, "Remove")
                                                onClick {
                                                    AppScope.launch {
                                                        val shelf = bookshelf.value
                                                        if (shelf != null) {
                                                            val updatedShelf = shelf.copy(
                                                                bookIds = shelf.bookIds - book.id
                                                            )
                                                            BookshelfRepository.updateBookshelf(updatedShelf)
                                                            bookshelf.value = updatedShelf
                                                            books.value = books.value - book
                                                        }
                                                    }
                                                }
                                                themeChoice = ThemeDerivation { it.copy(foreground = Color.red).withBack }.onNext
                                            }
                                        }
                                    }
                                    repeat(3 - rowBooks.size) {
                                        expanding.space(1.0)
                                    }
                                }
                            }
                        }
                    }

                    shownWhen { books().isNotEmpty() && viewMode() == ViewMode.List }.scrolls.col {
                        reactiveSuspending {
                            clearChildren()
                            for (book in books()) {
                                row {
                                    gap = 0.5.rem

                                    expanding.BookListItem(book) {
                                        if (!editMode.value) {
                                            mainPageNavigator.navigate(BookDetailPage(book.id))
                                        }
                                    }

                                    shownWhen { editMode() }.button {
                                        icon(Icon.delete, "Remove")
                                        onClick {
                                            AppScope.launch {
                                                val shelf = bookshelf.value
                                                if (shelf != null) {
                                                    val updatedShelf = shelf.copy(
                                                        bookIds = shelf.bookIds - book.id
                                                    )
                                                    BookshelfRepository.updateBookshelf(updatedShelf)
                                                    bookshelf.value = updatedShelf
                                                    books.value = books.value - book
                                                }
                                            }
                                        }
                                        themeChoice = ThemeDerivation { it.copy(foreground = Color.red).withBack }.onNext
                                    }
                                }
                            }
                        }
                    }

                    // FAB to add books
                    shownWhen { !editMode() && books().isNotEmpty() }
                    // TODO: Implement floating action button
                }
            }
        }
    }
}
