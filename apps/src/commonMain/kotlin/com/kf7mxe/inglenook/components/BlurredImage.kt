package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.appTheme
import com.kf7mxe.inglenook.cache.blurServerImageAndCacheImage
import com.kf7mxe.inglenook.cache.getBlurredCachedImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.persistedThemeSettings
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.kiteui.views.direct.unpadded
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending


fun ViewWriter.blurredImage(book: Reactive<Book?>, shown:Reactive<Boolean>) {
    val blurSettings = persistedThemeSettings.value

    val blurredImage = rememberSuspending {
        val imageToBlur = book()?.let { b ->
            b.coverImageId?.let { coverImageId ->
                jellyfinClient.value?.getImageUrl(coverImageId, b.id)
            }
        }?.let {
            ImageRemote(it)
        }

        val imageUrl: String? = book()?.let { b ->
            b.coverImageId?.let { coverImageId ->
                jellyfinClient.value?.getImageUrl(coverImageId, b.id)
            }
        }

        val cachedImageFileName = imageUrl?.let {
            "${blurSettings.blurRadius}-${it.split("Items/").last().replace("/", "-")}"
        }
        var cachedImage = cachedImageFileName?.let {
            getBlurredCachedImage(cachedImageFileName)
        }

        if (cachedImage == null) {
            cachedImage = imageUrl?.let { imageUrl ->
                imageToBlur?.let { recipeImage ->
                    blurServerImageAndCacheImage(
                        cachedImageFileName!!,
                        recipeImage,
                        blurSettings.blurRadius,
                        appTheme().background
                    )
                }
            }
        }
        cachedImage
    }

//                blurredImage(
//                    imageSource = blurredImage,
//                    blurRadius = blurSettings.blurRadius
//                )
    unpadded.image {
       rView::shown {
            shown()
        }
        scaleType = ImageScaleType.Crop
        ::source {
            blurredImage()
        }
    }
}