package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.pause
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.screens.openEbook
import com.lightningkite.kiteui.views.atBottomCenter
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke

fun ViewWriter.featuredBookCard(
    book: Reactive<Book>,
    onClick: suspend () -> Unit
) {
    col {
        centered.card.col {
            sizeConstraints(width = 25.rem, height = 25.rem).frame {
                centered.coverImage(
                    imageId = { book().coverImageId },
                    itemId = { book().id },
                    fallbackIcon = Icon.book,
                    imageHeight = 22.rem,
                    scaleType = ImageScaleType.Fit
                )

                atBottomCenter.button {
                    themeChoice += ImportantSemantic
                    centered.row {
                        icon {
                            ::source {
                                if (book().itemType == ItemType.Ebook) Icon.book.copy(3.5.rem, 3.5.rem)
                                else if (PlaybackState.currentBook()?.id == book().id && PlaybackState.isPlaying()) Icon.pause.copy(
                                    3.5.rem,
                                    3.5.rem
                                )
                                else Icon.playArrow.copy(3.5.rem, 3.5.rem)
                            }
                            ::description { if (book().itemType == ItemType.Ebook) "Ebook" else "Play" }
                        }
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
//
            }
            button {
                col {
                    text {
                        ::content { book().title }
                        ellipsis = true
                        lineClamp = 1
                    }
                    subtext {
                        ::content {
                            book().authors.takeIf { it.isNotEmpty() }?.map { it.name }?.joinToString(", ")
                                ?: "Unknown Author"
                        }
                        ellipsis = true
                        lineClamp = 1
                    }
                    bookStatusIndicator(book)
                }
                this.onClick { onClick() }
            }
        }
    }
}

fun ViewWriter.bookCard(
    book: Reactive<Book>,
    onPlayClick: (suspend (Book) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    centered. card.sizeConstraints(width = 14.rem, height = 24.rem).col {
        // Cover image with click
        button {
//            padding = 0.rem
            col {
                centered.frame {
                    coverImage(
                        imageId = { book().coverImageId },
                        itemId = { book().id },
                        fallbackIcon = Icon.book,
                        imageHeight = 12.rem,
                        scaleType = ImageScaleType.Fit
                    )
                }
            }
            this.onClick { onClick() }
        }
        row {
            button {
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
                    bookStatusIndicator(book)
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
