package com.kf7mxe.inglenook.ui

import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.createObjectURL
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.reactive.core.Reactive
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.Image
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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


//actual suspend fun blurAndCacheImage(
//    localPath: String,
//    image: ImageSource
//): ImageSource? {
//    database = database ?: getDatabase()
//    val splitDirectoryAndFileName = localPath.split("/")
//    val directoryName = splitDirectoryAndFileName[0]
//    val fileName = splitDirectoryAndFileName[1]
//    println("in read image from storage blur and cache")
//    val image = database?.transaction("images") {
//        println("${directoryName}/${fileName}")
//        val result = objectStore("images").get(Key("${directoryName}/${fileName}"))
//        println("result ${result}")
//        if (result != undefined) result as Blob
//        else null
//    }
////    img.crossOrigin = "Anonymous"; // Avoid CORS issues
//    if (image == null) return null
//
//    return suspendCoroutine { continuation ->
//        val imageElement = Image()
//        val objectUrl = createObjectURL(image)
//        println("Object url ${objectUrl}")
//        imageElement.src = objectUrl
//        imageElement.crossOrigin = "Anonymous"
//
//        imageElement.onload = {
//            println("in image onload")
//
//            // Create a canvas to apply the blur
//            val canvas = document.createElement("canvas") as HTMLCanvasElement
//            val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
//            canvas.width = imageElement.width /8
//            canvas.height = imageElement.height /8
//
//            // Apply blur filter (proportional to canvas size)
//            ctx.filter = "blur(20px)"
//            ctx.drawImage(imageElement, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
//
//            // Convert the canvas to a Blob and resume the coroutine
//            canvas.toBlob({ blurredImageBlob ->
//                if (blurredImageBlob != null) {
//                    GlobalScope.launch {
//                        database?.writeTransaction("blurredImageCache") {
//                            val store = objectStore("blurredImageCache")
//                            println("Saving blurred image to cache")
//                            store.add(blurredImageBlob, Key("${directoryName}/${fileName}"))
//                        }
//                        continuation.resume(blurredImageBlob?.let {ImageRaw(it)}) // Resume coroutine with result
//                    }
//                } else {
//                    continuation.resume(null) // Resume with null if conversion fails
//                }
//            }, "image/jpeg")
//        }
//
//        imageElement.onerror = { _: Event, _: String, _: Int, _: Int, _: Any? ->
//            println("Failed to load image.")
//            continuation.resume(null) // Resume with null if the image fails to load
//        }
//    }
//    return null
//}