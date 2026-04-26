package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.appTheme
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.pause
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.screens.openEbook
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.rememberSuspending

fun ViewWriter.bookListItem(
    book: Reactive<Book>,
    onClick: suspend () -> Unit
) {
    val coverDominantColor = rememberSuspending {
        getDominantColor(book())
    }
    card.row {
        dynamicTheme {
            getSemanticForBookBackground(coverDominantColor(),appTheme().background.closestColor(), CardSemantic)
        }
        // Play button
        centered.col {
            centered.button {
                centered.icon {
                    ::source {
                        if (book().itemType == ItemType.Ebook) Icon.book else {
                            if (PlaybackState.currentBook()?.id == book().id && PlaybackState.isPlaying()) {
                                Icon.pause
                            } else Icon.playArrow
                        }
                    }
                    ::description { if (book().itemType == ItemType.Ebook) "Ebook" else "Play" }
                }
                themeChoice += ImportantSemantic
                onClick {
                    if (book().itemType == ItemType.Ebook) {
                        val currentBook = book.invoke()
                        openEbook(currentBook.id, this@bookListItem)
                    } else {
                        if (book() == PlaybackState.currentBook && PlaybackState.isPlaying()) {
                            PlaybackState.pause()
                        } else {
                            val startPosition = book().userData?.playbackPositionTicks ?: 0
                            PlaybackState.play(book(), startPosition)
                        }
                    }
                }
            }
        }

        // Main content
        expanding.button {
            row {
                // Thumbnail
                coverImage(
                    imageId = { book().coverImageId },
                    itemId = { book().id },
                    fallbackIcon = Icon.book
                )

                // Book info
                expanding.col {
                    text {
                        ::content { book().title }
                        ellipsis = true
                    }
                    subtext {
                        ::content { book().authors.map { it.name }.joinToString(", ").ifEmpty { "Unknown Author" } }
                        ellipsis = true
                    }
                    // Series info if available
                    subtext {
                        ::shown { book().seriesName != null }
                        ::content {
                            val bookData = book()
                            if (bookData.indexNumber != null) {
                                "${bookData.seriesName} #${bookData.indexNumber}"
                            } else {
                                bookData.seriesName ?: ""
                            }
                        }
                    }
                    bookStatusIndicator(book)
                }

                centered.icon(Icon.chevronRight, "View")
            }
            this.onClick { onClick() }
        }
    }
}
