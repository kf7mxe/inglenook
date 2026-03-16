package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.pause
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.screens.openEbook
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookCard(
    book: Reactive<Book>,
    onPlayClick: (suspend (Book) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    centered.card.sizeConstraints(width = 14.rem, height = 22.rem).col {
        // Cover image with click
        button {
            padding = 0.rem
            col {
                centered.frame {
                    CoverImage(
                        imageId = { book().coverImageId },
                        itemId = { book().id },
                        fallbackIcon = Icon.book,
                        imageHeight = 12.rem,
                        imageWidth = 14.rem,
                        scaleType = ImageScaleType.Fit
                    )
                }
            }
            this.onClick { onClick() }
        }
        row {
            expanding.button {
                col {
                    text {
                        ::content { book().title }
                        ellipsis = true
                        lineClamp = 2
                    }
                    subtext {
                        ::content {
                            book().authors.takeIf { it.isNotEmpty() }?.map { it.name }?.joinToString(",")
                                ?: "Unknown Author"
                        }
                        ellipsis = true
                        lineClamp = 2
                    }
                    BookStatusIndicator(book)
                }
                this.onClick { onClick() }
            }
            col {
                centered.button {
                    themeChoice += ImportantSemantic
                    centered.icon {
                        ::source {
                            if (book().itemType == ItemType.Ebook) Icon.book else {
                                if (PlaybackState.currentBook()?.id == book().id && PlaybackState.isPlaying()) {
                                    Icon.pause
                                } else {
                                    Icon.playArrow
                                }
                            }
                        }
                        ::description { if (book().itemType == ItemType.Ebook) "Ebook" else "Play" }
                    }
                    onClick {
                        val currentBook = book.invoke()
                        if (book().itemType == ItemType.Ebook)
                            openEbook(currentBook.id, this@button)
                        else {
                            if (currentBook == PlaybackState.currentBook && PlaybackState.isPlaying()) {
                                PlaybackState.pause()
                            } else {
                                val startPosition = currentBook.userData?.playbackPositionTicks ?: 0
                                PlaybackState.play(currentBook, startPosition)
                            }
                        }
                    }
                }
            }
        }
    }
}
