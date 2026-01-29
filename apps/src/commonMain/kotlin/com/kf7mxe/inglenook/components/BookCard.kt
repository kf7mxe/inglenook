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

fun ViewWriter.BookCard(book: AudioBook, onClick: () -> Unit) {
    button {
        col {
            gap = 0.5.rem

            // Cover image
            sizedBox(SizeConstraints(width = 8.rem, height = 12.rem)).frame {
                if (book.coverImageId != null) {
                    val client = jellyfinClient.value
                    image {
                        source = ImageRemote(client?.getImageUrl(book.coverImageId) ?: "")
                        scaleType = ImageScaleType.Crop
                    }
                } else {
                    centered.icon(Icon.book, book.title)
                }
            }

            // Title and author
            col {
                gap = 0.rem
                text {
                    content = book.title
                    ellipsis = true
                }
                subtext {
                    content = book.authors.firstOrNull() ?: "Unknown Author"
                    ellipsis = true
                }
            }
        }
        this.onClick { onClick() }
    }
}
