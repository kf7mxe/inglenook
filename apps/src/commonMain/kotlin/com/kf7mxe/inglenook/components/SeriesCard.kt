package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Series
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.book
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.context.invoke

fun ViewWriter.SeriesCard(
    series: Reactive<Series>,
    onClick: suspend () -> Unit
) {
    val cachedCover = rememberSuspending {
        val seriesData = series()
        jellyfinClient().fetchCoverImage(seriesData.imageId)
    }

    centered.card.button {
        col {
            // Cover image
            centered.sizeConstraints(width = 9.rem, height = 12.rem).frame {
                // Series image (use first book's cover)
                image {
                    this.rView::shown {
                        series().imageId != null
                    }
                    ::source { cachedCover() }
                    scaleType = ImageScaleType.Crop
                }
                // Placeholder icon if no image
                centered.icon {
                    ::shown {
                        series().imageId == null
                    }
                    source = Icon.book
                    ::description { series().name }
                }
            }

            // Series name and book count
            col {
                gap = 0.25.rem
                text {
                    ::content { series().name }
                    ellipsis = true
                }
                subtext {
                    ::content {
                        val count = series().bookCount
                        if (count == 1) "1 book" else "$count books"
                    }
                }
            }
        }
        this.onClick { onClick() }
    }
}
