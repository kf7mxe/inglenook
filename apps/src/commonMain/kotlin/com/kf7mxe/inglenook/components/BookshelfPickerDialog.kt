package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.dynamicTheme
import com.kf7mxe.inglenook.Bookshelf
import com.kf7mxe.inglenook.check
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.kf7mxe.inglenook.storage.NowPlayingSemantic
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.mutableRememberSuspending
import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun ViewWriter.BookshelfPickerDialog(
    bookId: String,
    onDismiss: () -> Unit
) {
    val bookshelves = mutableRememberSuspending { BookshelfRepository.getAllBookshelves() }
    val bookshelvesContainingBook = mutableRememberSuspending {
        BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet()
    }
    val showCreateNew = Signal(false)
    val newBookshelfName = Signal("")

    fun refreshBookshelves() {
        AppScope.launch {
            bookshelves.value = BookshelfRepository.getAllBookshelves()
            bookshelvesContainingBook.value = BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet()
        }
    }

    card.frame {
     padded.col {
            // Header
            row {
                expanding.h3 { content = "Add to Bookshelf" }
                button {
                    icon(Icon.close, "Close")
                    onClick { onDismiss() }
                }
            }

            // Create new bookshelf section
            shownWhen { !showCreateNew() }.button {
                row {
                    icon(Icon.add, "Add")
                    text("Create New Bookshelf")
                }
                onClick { showCreateNew.value = true }
                themeChoice += ImportantSemantic
            }

            shownWhen { showCreateNew() }.card.col {
                text { content = "New Bookshelf" }
                textInput {
                    hint = "Bookshelf name"
                    content bind newBookshelfName
                }
                row {
                    button {
                        text("Cancel")
                        onClick {
                            showCreateNew.value = false
                            newBookshelfName.value = ""
                        }
                    }
                    expanding.button {
                        text("Create & Add")
                        onClick {
                            if (newBookshelfName.value.isNotBlank()) {
                                AppScope.launch {
                                    val newShelf = BookshelfRepository.createBookshelf(newBookshelfName.value)
                                    BookshelfRepository.addBookToBookshelf(newShelf._id, bookId)
                                    newBookshelfName.value = ""
                                    showCreateNew.value = false
                                    bookshelves.value = BookshelfRepository.getAllBookshelves()
                                    bookshelvesContainingBook.value = BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet()
                                }
                            }
                        }
                        themeChoice += ImportantSemantic
                    }
                }
            }

            separator()

            // Existing bookshelves
            shownWhen { bookshelves.state().ready && bookshelves().isEmpty() }.centered.col {
                text { content = "No bookshelves yet" }
                subtext { content = "Create one above to get started" }
            }

            shownWhen { bookshelves.state().ready && bookshelves().isNotEmpty() }.col {
                gap = 0.rem

                forEach(bookshelves) { bookshelf ->
                    val shelfId = bookshelf._id
                    button {
                        row {
                            icon {
                                ::source { if (shelfId in bookshelvesContainingBook()) Icon.check else Icon.add }
                                ::description { if (shelfId in bookshelvesContainingBook()) "In bookshelf" else "Not in bookshelf" }
                            }

                            expanding.col {
                                gap = 0.rem
                                text { content = bookshelf.name }
                                subtext { content = "${bookshelf.bookIds.size} books" }
                            }
                        }
                        onClick {
                            AppScope.launch {
                                val currentContaining = BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet()
                                if (shelfId in currentContaining) {
                                    BookshelfRepository.removeBookFromBookshelf(shelfId, bookId)
                                } else {
                                    BookshelfRepository.addBookToBookshelf(shelfId, bookId)
                                }
                                bookshelves.value = BookshelfRepository.getAllBookshelves()
                                bookshelvesContainingBook.value = BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet()
                            }
                        }
                        dynamicTheme {
                            if (shelfId in bookshelvesContainingBook()) SelectedSemantic else null
                        }
                    }
                    separator()
                }
            }

            // Done button
            button {
                centered.text("Done")
                onClick { onDismiss() }
                themeChoice += ImportantSemantic
            }
        }
    }
}
