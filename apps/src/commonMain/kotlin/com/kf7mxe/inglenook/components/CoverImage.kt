package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.models.Dimension
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.rememberSuspending

/**
 * Reusable cover image with fallback icon.
 *
 * [imageId] and [itemId] are suspend lambdas called inside reactive contexts.
 */
fun ViewWriter.coverImage(
    imageId: suspend () -> String?,
    itemId: suspend () -> String?,
    fallbackIcon: Icon = Icon.book,
    imageHeight: Dimension = 7.rem,
    imageWidth: Dimension = 5.rem,
    scaleType: ImageScaleType = ImageScaleType.Crop
) {
    val cachedCover = rememberSuspending {
        val test = jellyfinClient().fetchCoverImage(imageId(), itemId())
        println("DEBUG test ${test}")
        test
    }
    val hasImage = rememberSuspending {
        val hasImage = imageId() != null && cachedCover() != null
        println("DEBUG hasImage ${hasImage}")
        hasImage
    }

    sizeConstraints(height = imageHeight, width = imageWidth).frame {
        themed(ImageSemantic).image {
            this.rView::shown { hasImage() == true }
            ::source { cachedCover() }
            this.scaleType = scaleType
        }
        centered.icon {
            ::shown { hasImage() != true }
            source = fallbackIcon
            description = "Cover"
        }
    }
}

/**
 * Overload for static (non-reactive) image data.
 */
fun ViewWriter.coverImage(
    imageId: String?,
    itemId: String,
    fallbackIcon: Icon = Icon.book,
    imageHeight: Dimension = 7.rem,
    imageWidth: Dimension = 5.rem,
    scaleType: ImageScaleType = ImageScaleType.Crop
) {
    if (imageId != null) {
        val cachedCover = rememberSuspending {
            jellyfinClient.value.fetchCoverImage(imageId, itemId)
        }
        sizeConstraints(height = imageHeight, width = imageWidth).frame {
            themed(ImageSemantic).image {
                ::source { cachedCover() }
                this.scaleType = scaleType
            }
        }
    } else {
        sizeConstraints(height = imageHeight, width = imageWidth).frame {
            centered.icon(fallbackIcon, "Cover")
        }
    }
}
