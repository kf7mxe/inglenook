package com.kf7mxe.inglenook.cache

import com.juul.indexeddb.Key
import com.kf7mxe.inglenook.database
import com.kf7mxe.inglenook.getDatabase
import com.kf7mxe.inglenook.storage.getCacheFileName
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageSource
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(DelicateCoroutinesApi::class)
actual suspend fun blurServerImageAndCacheImage(
    location: String,
    image: ImageRemote,
    blurRadius: Float
): ImageSource? {
    database = database ?: getDatabase()

    return suspendCoroutine { continuation ->
        val imageElement = Image()
        imageElement.crossOrigin = "Anonymous"
//        imageElement.crossOrigin = selectedApi.value.api.toString()

        imageElement.src = image.url

        imageElement.onload = {
            println("in image onload")

            // Create a canvas to apply the blur
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
            canvas.width = imageElement.width /8
            canvas.height = imageElement.height /8

            // Apply blur filter (proportional to canvas size)
            ctx.filter = "blur(${blurRadius}px)"
            ctx.drawImage(imageElement, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())

            // Convert the canvas to a Blob and resume the coroutine
            canvas.toBlob({ blurredImageBlob ->
                if (blurredImageBlob != null) {
                    GlobalScope.launch {
                        database?.writeTransaction("blurredImageCache") {
                            val store = objectStore("blurredImageCache")
                            println("Saving blurred image to cache")
                            store.add(blurredImageBlob, Key(getCacheFileName(location)))
                        }
                        continuation.resume(blurredImageBlob?.let {ImageRaw(it)}) // Resume coroutine with result
                    }
                } else {
                    continuation.resume(null) // Resume with null if conversion fails
                }
            }, "image/jpeg")
        }

        imageElement.onerror = { _: Event, _: String, _: Int, _: Int, _: Any? ->
            println("Failed to load image.")
            continuation.resume(null) // Resume with null if the image fails to load
        }
    }
    return null
}

actual suspend fun getBlurredCachedImage(localPath: String): ImageSource? {
    database = database ?: getDatabase()
    println("DEBUG getBlurredCachedImage ${localPath}")
    val image = database?.transaction("blurredImageCache") {
        val result = objectStore("blurredImageCache").get(Key(getCacheFileName(localPath)))
        if(result != undefined) result as Blob
        else null
    }
    return image?.let { ImageRaw(it) }
}