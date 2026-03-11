package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.checkCircle
import com.kf7mxe.inglenook.pause
import com.kf7mxe.inglenook.playArrow
import com.lightningkite.kiteui.views.direct.shownWhen
import com.lightningkite.kiteui.views.direct.progressBar
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.screens.EbookReaderPage
import com.kf7mxe.inglenook.screens.openEbook
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookCard(
    book: Reactive<Book>,
    onPlayClick: (suspend (Book) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    val cachedCover = rememberSuspending {
        val bookData = book()
        jellyfinClient().fetchCoverImage(bookData.coverImageId, bookData.id)
    }

    centered.card.sizeConstraints(width = 14.rem, height = 22.rem).col {
        // Play button overlay at bottom right
        button {
            padding = 0.rem
            col {
                // Cover image with play button overlay
                centered.frame {
                    // Cover image
                    themed(ImageSemantic).sizeConstraints(height = 12.rem).centered.image {
                        this.rView::shown {
                            book().coverImageId != null
                        }
                        ::source { cachedCover() }
                        scaleType = ImageScaleType.Fit
                    }
                    centered.sizeConstraints(height = 12.rem).icon {
                        ::shown {
                            book().coverImageId == null
                        }
                        source = Icon.book
                        ::description { book().title }
                    }
                }


            }
            this.onClick { onClick() }
        }
        row {
            expanding.button {
                // Title and author (clickable to go to detail)
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
                    // Completed
                    shownWhen { book().userData?.played == true }.row {
                        icon(Icon.checkCircle, "Completed")
                        subtext { content = "Completed" }
                    }
                    // In progress
                    shownWhen {
                        val b = book()
                        (b.userData?.playbackPositionTicks ?: 0L) > 0L && b.userData?.played != true
                    }.row {
                        subtext {
                            ::content {
                                val b = book()
                                val position = b.userData?.playbackPositionTicks ?: 0L
                                val dur = b.duration
                                val percent =
                                    if (dur > 0) ((position.toFloat() / dur) * 100).toInt().coerceAtMost(100) else 0
                                "$percent%"
                            }
                        }
                        expanding.progressBar {
                            ::ratio {
                                val b = book()
                                val position = b.userData?.playbackPositionTicks ?: 0L
                                val dur = b.duration
                                if (dur > 0) (position.toFloat() / dur).coerceAtMost(1f) else 0f
                            }
                        }
                    }
                    // Not started - show duration (audiobooks only)
                    shownWhen {
                        val b = book()
                        (b.userData?.playbackPositionTicks
                            ?: 0L) == 0L && b.userData?.played != true && b.itemType == ItemType.AudioBook
                    }.subtext {
                        ::content {
                            val durationTicks = book().duration
                            val totalSeconds = durationTicks / 10_000_000
                            val hours = totalSeconds / 3600
                            val minutes = (totalSeconds % 3600) / 60
                            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                        }
                    }
                }
                this.onClick { onClick() }
            }
            col {
                // Only show play button for audiobooks, show book icon for ebooks
                centered.button {
                    themeChoice += ImportantSemantic
                    // Show play icon for audiobooks, book icon for ebooks
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