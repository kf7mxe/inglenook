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
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
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
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Routable("/bookshelf/{bookshelfId}")
class BookshelfDetailPage(val bookshelfId: String) : Page {
    override val title: Reactive<String> = Constant("Bookshelf")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        val bookshelf = rememberSuspending {
            val uuid = Uuid.parse(bookshelfId)
            BookshelfRepository.getBookshelf(uuid)
        }
        val books = rememberSuspending {
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

        // Remove book from bookshelf
        fun removeBook(bookId: String) {
            AppScope.launch {
                try {
                    val uuid = Uuid.parse(bookshelfId)
                    BookshelfRepository.removeBookFromBookshelf(uuid, bookId)
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

                                                ::placer{ RecyclerViewPlacerVerticalGrid(3) }
                                                children(books, { it.id }) { book ->
                                                    col {
                                                        BookCard(book) {
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
                                                        BookListItem(book) {
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
