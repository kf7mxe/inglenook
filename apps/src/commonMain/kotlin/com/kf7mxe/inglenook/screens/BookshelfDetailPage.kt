package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.check
import com.kf7mxe.inglenook.components.bookCard
import com.kf7mxe.inglenook.components.bookListItem
import com.kf7mxe.inglenook.components.AddBookDialog
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.edit
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.danger
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.mutableRememberSuspending
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Routable("/bookshelf/{bookshelfId}")
class BookshelfDetailPage(val bookshelfId: String) : Page {
    override val title: Reactive<String> = Constant("Bookshelf")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        val bookshelf = mutableRememberSuspending {
            val uuid = Uuid.parse(bookshelfId)
            BookshelfRepository.getBookshelf(uuid)
        }
        val books = mutableRememberSuspending {
            val client = jellyfinClient.value
            bookshelf()?.bookIds?.mapNotNull { bookId ->
                try {
                    client?.getBook(bookId)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
        }
        val isEditing = Signal(false)

        fun refreshData() {
            AppScope.launch {
                val uuid = Uuid.parse(bookshelfId)
                bookshelf.value = BookshelfRepository.getBookshelf(uuid)
                val client = jellyfinClient.value
                books.value = bookshelf()?.bookIds?.mapNotNull { bookId ->
                    try {
                        client?.getBook(bookId)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
            }
        }

        // Remove book from bookshelf
        fun removeBook(bookId: String) {
            AppScope.launch {
                try {
                    val uuid = Uuid.parse(bookshelfId)
                    BookshelfRepository.removeBookFromBookshelf(uuid, bookId)
                    refreshData()
                } catch (e: Exception) {
                    // Ignore error
                }
            }
        }

        col {
            gap = 0.rem

            // Header with shelf name and actions
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.h2 { ::content { bookshelf()?.name ?: "Bookshelf" } }

                // Add books button
                button {
                    icon(Icon.add, "Add Books")
                    onClick {
                        dialog { dismiss ->
                            AddBookDialog(
                                bookshelfId = Uuid.parse(bookshelfId),
                                currentBookIds = remember { bookshelf()?.bookIds?.toSet() ?: emptySet() },
                                onDismiss = { dismiss() },
                                onRefresh = { refreshData() }
                            )
                        }
                    }
                }

                // Edit toggle
                button {
                    icon {
                        ::source { if (isEditing()) Icon.check else Icon.edit }
                        ::description { if (isEditing()) "Done" else "Edit" }
                    }
                    onClick { isEditing.value = !isEditing.value }
                }

                // View mode toggle
                viewModeToggleButton()
            }

            separator()

            expanding.frame {
                // Loading state
                shownWhen { !books.state().ready }.centered.inglenookActivityIndicator()

                // Error state
//                shownWhen { !books.state().ready && !isLoading() }.centered.col {
//                    gap = 0.5.rem
//                    text { ::content { errorMessage() ?: "" } }
//                    button {
//                        text("Retry")
//                        onClick { loadData() }
//                    }
//                }

                // Content
                shownWhen { books.state().ready }.frame {
                    // Empty state
                    shownWhen { books()?.isEmpty() == true }.EmptyState(
                        icon = Icon.book,
                        title = "No books in this bookshelf",
                        description = "Add books from the book detail page"
                    )


                    col {
                        gap = 1.rem


                        expanding.swapView {
                            swapping(
                                current = {
                                    com.kf7mxe.inglenook.viewMode()
                                },
                                views = { viewMode ->
                                    when (viewMode) {
                                        ViewMode.Grid -> {
                                            recyclerView {

                                                ::placer{ RecyclerViewPlacerVerticalGrid(2) }
                                                children(books, { it.id }) { book ->
                                                    col {
                                                        bookCard(book) {
                                                            mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                                        }
                                                            shownWhen { isEditing() }.centered.button {
                                                                danger.icon(Icon.remove, "Remove")
                                                                onClick {
                                                                    removeBook(book().id)
                                                                }
                                                            }
                                                    }

                                                }
                                            }
                                        }

                                        ViewMode.List -> {
                                            recyclerView {
                                                children(books, { it.id }) { book ->
                                                    expanding.row {
                                                        shownWhen { isEditing() }.centered.col {
                                                            centered.button {
                                                                danger.icon(Icon.remove, "Remove")
                                                                onClick {
                                                                    removeBook(book().id)
                                                                }
                                                            }
                                                        }
                                                        bookListItem(book) {
                                                            mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                                        }
                                                    }

                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
