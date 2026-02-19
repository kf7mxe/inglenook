package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.collectionsBookmark
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImportantSemantic
import com.lightningkite.kiteui.models.SizeConstraints
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.activityIndicator
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.h3
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.scrolling
import com.lightningkite.kiteui.views.direct.separator
import com.lightningkite.kiteui.views.direct.shownWhen
import com.lightningkite.kiteui.views.direct.sizedBox
import com.lightningkite.kiteui.views.direct.subtext
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textInput
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.mutableRememberSuspending
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.value
import kotlin.uuid.ExperimentalUuidApi

@Routable("bookshelf")
class BookshelfPage : Page {
    override val title get() = Constant("Bookshelf")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {

        val bookshelves = mutableRememberSuspending {
            BookshelfRepository.getAllBookshelves()
        }

        col {

            fun ViewWriter.addBookshelfDialog() {
                dialog {dismiss ->
                    val newBookshelfName = Signal("")
                    col {
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
                                    newBookshelfName.value = ""
                                    dismiss()
                                }
                            }
                            expanding.button {
                                text("Create")
                                onClick {
                                    val name = newBookshelfName.value.trim()
                                    if (name.isNotEmpty()) {
                                        BookshelfRepository.createBookshelf(name)
                                        bookshelves.value = BookshelfRepository.getAllBookshelves()
                                        newBookshelfName.value = ""
                                    }
                                }
                                themeChoice += ImportantSemantic
                            }
                        }
                    }
                }

            }

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
                    onClick { addBookshelfDialog() }
                    themeChoice += ImportantSemantic
                }
            }

            separator()
            // Loading state
            shownWhen { !bookshelves.state().ready }.centered.activityIndicator()

            // Empty state
            shownWhen { bookshelves.state().ready && bookshelves().isEmpty() }.centered.col {
                gap = 1.rem
                padding = 2.rem

                icon(Icon.collectionsBookmark.copy(width = 4.rem, height = 4.rem), "Bookshelves")
                centered.h3 { content = "No Bookshelves Yet" }
                centered.text { content = "Create a bookshelf to organize your audiobooks" }
                centered.buttonTheme.button {
                    row {
                        gap = 0.25.rem
                        icon(Icon.add, "Add")
                        text("Create Bookshelf")
                    }
                    onClick {addBookshelfDialog() }
                    themeChoice += ImportantSemantic
                }
            }

            // Bookshelves grid
            shownWhen { bookshelves.state().ready && bookshelves().isNotEmpty() }.scrolling.col {
                padding = 1.rem
                gap = 1.rem


                forEach(bookshelves) { bookshelf ->
                    button {
                        col {
                            gap = 0.5.rem

                            // Bookshelf icon
                            sizedBox(SizeConstraints(width = 8.rem, height = 8.rem)).centered.frame {
                                icon(
                                    Icon.collectionsBookmark.copy(width = 4.rem, height = 4.rem),
                                    bookshelf.name
                                )
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
                            mainPageNavigator.navigate(BookshelfDetailPage(bookshelf._id.toString()))
                        }
                    }
                }
            }
        }


    }
}