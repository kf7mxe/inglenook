package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
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
import com.kf7mxe.inglenook.screens.EbookReaderPage
import com.kf7mxe.inglenook.screens.openEbook
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.card
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookListItem(
    book: Reactive<Book>,
    onPlayClick: (suspend (Book) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    val cachedCover = rememberSuspending {
        val bookData = book()
        jellyfinClient().fetchCoverImage(bookData.coverImageId, bookData.id)
    }

    card.row {

        // Play button
        centered.col {
            centered.button {
                centered.icon {
                    ::source { if (book().itemType == ItemType.Ebook) Icon.book else {
                        if( PlaybackState.currentBook()?.id == book().id && PlaybackState.isPlaying()) {
                        Icon.pause } else Icon.playArrow } }
                    ::description { if (book().itemType == ItemType.Ebook) "Ebook" else "Play" }
                }
                themeChoice += ImportantSemantic
                onClick {
                    val currentBook = book.invoke()
                    openEbook(currentBook.id, this@BookListItem)
                }
            }
        }
//
//        // Main content (clickable to go to detail)
        expanding.button {
            row {
                // Thumbnail
                sizeConstraints(height = 7.rem, width=5.rem).frame {
                    themed(ImageSemantic).image {
                        this.rView::shown {
                            book().coverImageId != null
                        }

                        ::source { cachedCover() }
                        scaleType = ImageScaleType.Crop
                    }
                    centered.icon {
                        ::shown {
                            book().coverImageId == null
                        }
                        source = Icon.book
                        ::description { book().title }
                    }
                }

//                // Book info
                expanding.col {

                    text {
                        ::content { book().title }
                        ellipsis = true
                    }

                    subtext {
                        ::content { book().authors.map{it.name}.joinToString(", ").ifEmpty { "Unknown Author" } }
                        ellipsis = true
                    }

                    // Series info if available
                    subtext {
                        ::shown {
                            book().seriesName != null
                        }
                        ::content {
                            val bookData = book()
                            if (bookData.indexNumber != null) {
                                "${bookData.seriesName} #${bookData.indexNumber}"
                            } else {
                                bookData.seriesName ?: ""
                            }
                        }
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
                    }.col {
                        subtext {
                            ::content {
                                val b = book()
                                val position = b.userData?.playbackPositionTicks ?: 0L
                                val dur = b.duration
                                val percent = if (dur > 0) ((position.toFloat() / dur) * 100).toInt().coerceAtMost(100) else 0
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
                        (b.userData?.playbackPositionTicks ?: 0L) == 0L && b.userData?.played != true && b.itemType == ItemType.AudioBook
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

                centered.icon(Icon.chevronRight, "View")
            }
            this.onClick { onClick() }
        }
    }
}
