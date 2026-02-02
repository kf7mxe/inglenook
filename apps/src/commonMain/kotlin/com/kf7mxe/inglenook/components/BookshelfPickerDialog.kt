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
import com.lightningkite.reactive.core.Signal
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun ViewWriter.BookshelfPickerDialog(
    bookId: String,
    onDismiss: () -> Unit
) {
    val bookshelves = Signal(BookshelfRepository.getAllBookshelves())
    val bookshelvesContainingBook = Signal(BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet())
    val showCreateNew = Signal(false)
    val newBookshelfName = Signal("")

    fun refreshBookshelves() {
        bookshelves.value = BookshelfRepository.getAllBookshelves()
        bookshelvesContainingBook.value = BookshelfRepository.getBookshelvesContainingBook(bookId).map { it._id }.toSet()
    }

    card.col {
        padding = 1.rem
        gap = 1.rem

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
                gap = 0.5.rem
                icon(Icon.add, "Add")
                text("Create New Bookshelf")
            }
            onClick { showCreateNew.value = true }
            themeChoice += ImportantSemantic
        }

        shownWhen { showCreateNew() }.card.col {
            gap = 0.5.rem
            padding = 0.75.rem

            text { content = "New Bookshelf" }
            textInput {
                hint = "Bookshelf name"
                content bind newBookshelfName
            }
            row {
                gap = 0.5.rem
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
                            val newShelf = BookshelfRepository.createBookshelf(newBookshelfName.value)
                            BookshelfRepository.addBookToBookshelf(newShelf._id, bookId)
                            newBookshelfName.value = ""
                            showCreateNew.value = false
                            refreshBookshelves()
                        }
                    }
                    themeChoice += ImportantSemantic
                }
            }
        }

        separator()

        // Existing bookshelves
        shownWhen { bookshelves().isEmpty() }.centered.col {
            padding = 1.rem
            text { content = "No bookshelves yet" }
            subtext { content = "Create one above to get started" }
        }

        shownWhen { bookshelves().isNotEmpty() }.col {
            gap = 0.rem

            // Render bookshelves
            for (bookshelf in bookshelves.value) {
                val shelfId = bookshelf._id
                val isInBookshelf = shelfId in bookshelvesContainingBook.value

                button {
                    row {
                        padding = 0.5.rem
                        gap = 0.75.rem

                        icon {
                            source = if (isInBookshelf) Icon.check else Icon.add
                            description = if (isInBookshelf) "In bookshelf" else "Not in bookshelf"
                        }

                        expanding.col {
                            gap = 0.rem
                            text { content = bookshelf.name }
                            subtext { content = "${bookshelf.bookIds.size} books" }
                        }
                    }
                    onClick {
                        // Toggle membership
                        if (shelfId in bookshelvesContainingBook.value) {
                            BookshelfRepository.removeBookFromBookshelf(shelfId, bookId)
                        } else {
                            BookshelfRepository.addBookToBookshelf(shelfId, bookId)
                        }
                        refreshBookshelves()
                    }
                    dynamicTheme {
                        if (shelfId in bookshelvesContainingBook()) SelectedSemantic else null
                    }
                }
                separator()
            }
        }

        // Done button
        expanding.button {
            centered.text("Done")
            onClick { onDismiss() }
            themeChoice += ImportantSemantic
        }
    }
}
