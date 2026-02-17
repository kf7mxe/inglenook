package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.screens.EbookReaderPage
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookListItem(
    audioBook: Reactive<AudioBook>,
    onPlayClick: (suspend (AudioBook) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    val cachedCover = rememberSuspending {
        val client = jellyfinClient()
        val bookData = audioBook()
        if (client != null && bookData.coverImageId != null) {
            ImageCache.get(client.getImageUrl(bookData.coverImageId, bookData.id))
        } else null
    }

    row {

        // Play button
        centered.col {
            centered.button {
                centered.icon {
                    ::source { if (audioBook().itemType == ItemType.Ebook) Icon.book else Icon.playArrow }
                    ::description { if (audioBook().itemType == ItemType.Ebook) "Ebook" else "Play" }
                }
                themeChoice += ImportantSemantic
                onClick {
                    val currentBook = audioBook.invoke()
                    // Only play audiobooks
                    if (currentBook.itemType == ItemType.AudioBook) {
                        if (onPlayClick != null) {
                            onPlayClick(currentBook)
                        } else {
                            val startPosition = currentBook.userData?.playbackPositionTicks ?: 0L
                            PlaybackState.play(currentBook, startPosition)
                        }
                    }
                    if(currentBook.itemType == ItemType.Ebook) {
                        mainPageNavigator.navigate(EbookReaderPage(currentBook.id))
                    }
                }
            }
        }
//
//        // Main content (clickable to go to detail)
        expanding.button {
            row {
                // Thumbnail
                sizeConstraints(height = 7.rem, width=5.rem).frame {
                    image {
                        this.rView::shown {
                            audioBook().coverImageId != null
                        }

                        ::source { cachedCover() }
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

//                // Book info
                expanding.col {

                    text {
                        ::content { audioBook().title }
                        ellipsis = true
                    }

                    subtext {
                        ::content { audioBook().authors.map{it.name}.joinToString(", ").ifEmpty { "Unknown Author" } }
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
