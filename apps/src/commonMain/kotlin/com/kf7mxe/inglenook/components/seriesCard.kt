package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Series
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.util.formatBookCount
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke
import kotlinx.coroutines.launch

fun ViewWriter.seriesCard(
    series: Reactive<Series>,
    onClick: suspend () -> Unit
) {
    centered.card.button {

        col {
            // Cover image
            centered.coverImage(
                imageId = { series().imageId },
                itemId = { series().coverBookId },
                fallbackIcon = Icon.book,
                imageHeight = 12.rem,
                imageWidth = 9.rem,
                scaleType = ImageScaleType.Crop
            )

            // Series name and book count
            col {
                gap = 0.25.rem
                text {
                    ::content { series().name }
                    ellipsis = true
                }
                subtext {
                    ::content { formatBookCount(series().bookCount) }
                }
            }
        }
        this.onClick { onClick() }
    }
}
