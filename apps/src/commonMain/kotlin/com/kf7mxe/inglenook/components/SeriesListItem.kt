package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.Series
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.util.formatBookCount
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke

fun ViewWriter.SeriesListItem(
    series: Reactive<Series>,
    onClick: suspend () -> Unit
) {
    expanding.button {
        row {
            // Thumbnail
            coverImage(
                imageId = { series().imageId },
                itemId = { series().id },
                fallbackIcon = Icon.book,
                imageHeight = 7.rem,
                imageWidth = 5.rem,
                scaleType = ImageScaleType.Crop
            )

            // Text Info
            expanding.col {
                text {
                    ::content { series().name }
                    ellipsis = true
                }
                subtext {
                    ::content { formatBookCount(series().bookCount) }
                    ellipsis = true
                }
            }

            centered.icon(Icon.chevronRight, "View")
        }

        this.onClick { onClick() }
    }
}
