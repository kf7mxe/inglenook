package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient

fun ViewWriter.BookListItem(book: AudioBook, onClick: () -> Unit) {
    button {
        row {
            gap = 1.rem
            padding = 0.5.rem

            // Thumbnail
            sizedBox(SizeConstraints(width = 4.rem, height = 6.rem)) {
                if (book.coverImageId != null) {
                    val client = jellyfinClient.value
                    image {
                        source = ImageRemote(client?.getImageUrl(book.coverImageId) ?: "")
                        scaleType = ImageScaleType.Crop
                    }
                } else {
                    themeChoice = ThemeDerivation {
                        it.copy(background = it.background.darken(0.1f)).withBack
                    }.onNext
                    centered.icon(Icon.book, book.title)
                }
            }

            // Book info
            expanding.col {
                gap = 0.25.rem

                text {
                    content = book.title
                    ellipsis = true
                }

                subtext {
                    content = book.authors.joinToString(", ").ifEmpty { "Unknown Author" }
                    ellipsis = true
                }

                // Series info if available
                if (book.seriesName != null) {
                    subtext {
                        val seriesText = if (book.indexNumber != null) {
                            "${book.seriesName} #${book.indexNumber}"
                        } else {
                            book.seriesName
                        }
                        content = seriesText
                    }
                }

                // Duration
                subtext {
                    val durationTicks = book.duration
                    val totalSeconds = durationTicks / 10_000_000
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    content = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                }

                // Progress bar if in progress
                val progress = book.userData?.playbackPositionTicks ?: 0L
                if (progress > 0L && book.duration > 0L) {
                    progressBar {
                        ratio = progress.toFloat() / book.duration
                    }
                }
            }

            centered.icon(Icon.chevronRight, "View")
        }
        this.onClick { onClick() }
    }
}
