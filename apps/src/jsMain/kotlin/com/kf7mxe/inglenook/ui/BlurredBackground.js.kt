package com.kf7mxe.inglenook.ui

import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.reactive.core.Reactive
import org.w3c.dom.HTMLElement

/**
 * JS implementation of blurred background using CSS filter.
 */
actual fun ViewWriter.blurredImage(
    imageSource: Reactive<ImageSource?>,
    blurRadius: Float
) {
    image {
        this@image.rView::shown {
            imageSource() != null
        }
        ::source {imageSource() }
        scaleType = ImageScaleType.Crop

        // Apply CSS blur filter
        (rView.native as? HTMLElement)?.style?.apply {
            filter = "blur(${blurRadius}px)"
            // Scale up slightly to hide blur edges
            transform = "scale(1.1)"
        }
    }
}
