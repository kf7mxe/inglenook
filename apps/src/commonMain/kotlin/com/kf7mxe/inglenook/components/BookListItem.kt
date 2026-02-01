package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.playArrow
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookListItem(
    audioBook: Reactive<AudioBook>,
    onPlayClick: ((AudioBook) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    row {

        // Play button
        centered.col {
            centered.button {
                centered.icon(Icon.playArrow, "Play")
                themeChoice += ImportantSemantic
                onClick {
                    val currentBook = audioBook.invoke()
                    if (onPlayClick != null) {
                        onPlayClick(currentBook)
                    } else {
                        val startPosition = currentBook.userData?.playbackPositionTicks ?: 0L
                        PlaybackState.play(currentBook, startPosition)
                    }
                }
            }
        }

        // Main content (clickable to go to detail)
        expanding.button {
            row {
                // Thumbnail
                sizeConstraints(width = 4.rem, height = 6.rem).frame {
                    image {
                        this.rView::shown {
                            audioBook().coverImageId != null
                        }

                        ::source {
                            val client = jellyfinClient()
                            val bookData = audioBook()
                            if (client != null && bookData.coverImageId != null) {
                                ImageRemote(client.getImageUrl(bookData.coverImageId, bookData.id))
                            } else null
                        }
                        scaleType = ImageScaleType.Crop
                    }
                    centered.icon {
                        ::shown {
                            audioBook().coverImageId == null
                        }
                        source = Icon.book
                        ::description { audioBook().title }
                    }
                }

                // Book info
                expanding.col {

                    text {
                        ::content { audioBook().title }
                        ellipsis = true
                    }

                    subtext {
                        ::content { audioBook().authors.joinToString(", ").ifEmpty { "Unknown Author" } }
                        ellipsis = true
                    }

                    // Series info if available
                    subtext {
                        ::shown {
                            audioBook().seriesName != null
                        }
                        ::content {
                            val bookData = audioBook()
                            if (bookData.indexNumber != null) {
                                "${bookData.seriesName} #${bookData.indexNumber}"
                            } else {
                                bookData.seriesName ?: ""
                            }
                        }
                    }

                    // Duration
                    subtext {
                        ::content {
                            val durationTicks = audioBook().duration
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
