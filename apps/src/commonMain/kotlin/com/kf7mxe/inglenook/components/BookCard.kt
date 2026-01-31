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
import com.lightningkite.kiteui.views.card
import com.lightningkite.reactive.core.Reactive

fun ViewWriter.BookCard(book: Reactive<AudioBook>, onClick: suspend () -> Unit) {
   centered. card.button {
        col {
            gap = 0.5.rem

            // Cover image
            centered.sizeConstraints(width = 8.rem, height = 12.rem).frame {

                   centered. image {
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
                        ::shown{
                            book().coverImageId == null

                        }
                        source = Icon.book
                        ::description  {book().title}
                    }

            }

            // Title and author
            col {
                gap = 0.rem
                text {
                    ::content{book().title}
                    ellipsis = true
                }
                subtext {
                    ::content{ book().authors.firstOrNull() ?: "Unknown Author" }
                    ellipsis = true
                }
            }
        }
        this.onClick { onClick() }
    }
}
