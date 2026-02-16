package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.appTheme
import com.kf7mxe.inglenook.cache.blurServerImageAndCacheImage
import com.kf7mxe.inglenook.cache.getBlurredCachedImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.persistedThemeSettings
import com.kf7mxe.inglenook.screens.BookDetailPage
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.kiteui.views.direct.unpadded
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending


fun ViewWriter.blurredImage(book: Reactive<AudioBook?>,shown:Reactive<Boolean>) {
    val blurSettings = persistedThemeSettings.value

    val blurredImage = rememberSuspending {
        println("DEBUg blurred image")
        val imageToBlur = book()?.let { b ->
            println("DEBUG in cover image url")
            b.coverImageId?.let { coverImageId ->
                println("DEBUG coverageImageId")
                jellyfinClient.value?.getImageUrl(coverImageId, b.id)
            }
        }?.let {
            ImageRemote(it)
        }
        println("DEBUG got imageToBlur")

        val imageUrl: String? = book()?.let { b ->
            println("DEBUG in cover image url")
            b.coverImageId?.let { coverImageId ->
                println("DEBUG coverageImageId")
                jellyfinClient.value?.getImageUrl(coverImageId, b.id)
            }
        }



        val cachedImageFileName = imageUrl?.let {
            println("DEBUG cached image url ${it}")
            println("DEBUG  it.split(\"Items/\").last() ${it.split("Items/").last().replace("/", "-")}")
            "${blurSettings.blurRadius}-${it.split("Items/").last().replace("/", "-")}"
        }
        var cachedImage = cachedImageFileName?.let {
            getBlurredCachedImage(cachedImageFileName)
        }

        println("DEBUG cached image ${cachedImage == null}")
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
        println("DBBUG cachedImage")
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