package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Reactive

fun ViewWriter.BookListItem(book: Reactive<AudioBook>, onClick: suspend () -> Unit) {
    button {
        row {
            gap = 1.rem
            padding = 0.5.rem

            // Thumbnail
            sizeConstraints(width = 4.rem, height = 6.rem).frame {
                    image {
                        this.rView::shown{
                            book().coverImageId != null
                        }

                        ::source {
                            val client = jellyfinClient()
                            if (client != null && book().coverImageId != null) {
                                ImageRemote(client.getImageUrl(book().coverImageId, book().id))
                            } else null
                        }
                        scaleType = ImageScaleType.Crop
                    }
                    centered.icon {
                        ::shown {
                            book().coverImageId == null
                        }
                        source = Icon.book
                        ::description { book().title}
                    }

            }

            // Book info
            expanding.col {
                gap = 0.25.rem

                text {
                    ::content {book().title }
                    ellipsis = true
                }

                subtext {
                    ::content {book().authors.joinToString(", ").ifEmpty { "Unknown Author" } }
                    ellipsis = true
                }

                // Series info if available
                    subtext {
                        ::shown {
                            book().seriesName == null
                        }
                        ::content {
                            if (book().indexNumber != null) {
                                "${book().seriesName} #${book().indexNumber}"
                            } else {
                                book().seriesName ?: ""
                            }
                        }
                }

                // Duration
                subtext {

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
