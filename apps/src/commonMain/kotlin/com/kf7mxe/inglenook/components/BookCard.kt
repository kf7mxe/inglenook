package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.atBottomEnd
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookCard(
    audioBook: Reactive<AudioBook>,
    onPlayClick: ((AudioBook) -> Unit)? = null,
    onClick: suspend () -> Unit
) {
    centered.card.col {
        // Play button overlay at bottom right

        button {
            col {
                // Cover image with play button overlay
                centered.sizeConstraints(width = 9.rem, height = 12.rem).frame {
                    // Cover image
                    ImageSemantic.onNext.centered.image {
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


            }
            this.onClick { onClick() }
        }
        row {
            expanding.button {
                // Title and author (clickable to go to detail)
                col {
                    text {
                        ::content { audioBook().title }
                        ellipsis = true
                    }
                    subtext {
                        ::content { audioBook().authors.firstOrNull() ?: "Unknown Author" }
                        ellipsis = true
                    }
                }
                this.onClick { onClick() }
            }
            col {
               centered.button {
                    themeChoice += ImportantSemantic
                    centered.icon(Icon.playArrow, "Play")
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
        }
    }
}