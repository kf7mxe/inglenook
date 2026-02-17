package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.Series // Assuming this import exists based on SeriesCard
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.context.invoke

fun ViewWriter.SeriesListItem(
    series: Reactive<Series>,
    onClick: suspend () -> Unit
) {
    // 1. Image Fetching Logic (Taken from SeriesCard)
    val cachedCover = rememberSuspending {
        val client = jellyfinClient()
        val seriesData = series()
        if (client != null && seriesData.imageId != null) {
            ImageCache.get(client.getImageUrl(seriesData.imageId))
        } else null
    }

    // 2. Main List Item Container
    expanding.button {
        row {
            // --- Thumbnail Section (Matched dimensions to BookListItem) ---
            sizeConstraints(height = 7.rem, width = 5.rem).frame {
                // Cover Image
                image {
                    this.rView::shown {
                        series().imageId != null
                    }
                    ::source { cachedCover() }
                    scaleType = ImageScaleType.Crop
                }

                // Placeholder Icon
                centered.icon {
                    ::shown {
                        series().imageId == null
                    }
                    source = Icon.book // Or Icon.folder if you prefer distinguishing series
                    ::description { series().name }
                }
            }

            // --- Text Info Section ---
            expanding.col {
                // Series Name
                text {
                    ::content { series().name }
                    ellipsis = true
                }

                // Book Count (Logic taken from SeriesCard)
                subtext {
                    ::content {
                        val count = series().bookCount
                        if (count == 1) "1 book" else "$count books"
                    }
                    ellipsis = true
                }
            }

            // --- Navigation Icon ---
            centered.icon(Icon.chevronRight, "View")
        }

        // Handle Click
        this.onClick { onClick() }
    }
}