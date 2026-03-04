package com.kf7mxe.inglenook.cache

import com.juul.indexeddb.Key
import com.kf7mxe.inglenook.database
import com.kf7mxe.inglenook.getDatabase
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.views.direct.createObjectURL
import kotlinx.browser.document
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import org.w3c.dom.events.Event
import kotlin.collections.get
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.add

private fun applyRadialGradientOverlay(
    ctx: CanvasRenderingContext2D,
    w: Double,
    h: Double,
    blurRadius: Float,
    overlayColor: Paint
) {
    ctx.filter = "none"
    ctx.globalCompositeOperation = "source-over"

    val cx = w / 2.0
    val cy = h / 2.0
    val radius = kotlin.math.sqrt(cx * cx + cy * cy)

    val gradient = ctx.createRadialGradient(cx, cy, 0.0, cx, cy, radius)
    val hexColor = overlayColor.closestColor().toWeb()

    gradient.addColorStop(0.0, "rgba(0,0,0,0)")
    gradient.addColorStop(1.0, hexColor)

    val calculatedAlpha = (blurRadius / 20.0).coerceIn(0.0, 0.6)

    ctx.globalAlpha = calculatedAlpha
    ctx.fillStyle = gradient
    ctx.fillRect(0.0, 0.0, w, h)
}

actual suspend fun blurRemoteImageAndCache(
    location: String,
    image: ImageRemote,
    blurRadius: Float,
    overlayColor: Paint
): ImageSource? {
    database = database ?: getDatabase()

    return suspendCoroutine { continuation ->
        val imageElement = Image()
        imageElement.crossOrigin = "Anonymous"
        imageElement.src = image.url

        imageElement.onload = {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

            // Downscale for performance and style
            canvas.width = imageElement.width / 8
            canvas.height = imageElement.height / 8

            val w = canvas.width.toDouble()
            val h = canvas.height.toDouble()

            // 1. Apply Blur
            ctx.filter = "blur(${blurRadius}px)"
            ctx.drawImage(imageElement, 0.0, 0.0, w, h)

            // 2. Blend the Radial Gradient Overlay
            applyRadialGradientOverlay(ctx, w, h, blurRadius, overlayColor)

            // 3. Convert to Blob
            canvas.toBlob({ blurredImageBlob ->
                if (blurredImageBlob != null) {
                    AppScope.launch {
                        database?.writeTransaction("blurredImageCache") {
                            val store = objectStore("blurredImageCache")
                            store.add(blurredImageBlob, Key(location))
                        }
                        continuation.resume(ImageRaw(blurredImageBlob))
                    }
                } else {
                    continuation.resume(null)
                }
            }, "image/jpeg")
        }

        imageElement.onerror = { _, _, _, _, _ ->
            continuation.resume(null)
        }
    }
}

actual suspend fun clearImageCaches() {
    database = database ?: getDatabase()
    database?.writeTransaction("blurredImageCache") {
        objectStore("blurredImageCache").clear()
    }
    clearPersistedImageCache()
}

actual suspend fun getBlurredCachedImage(localPath: String): ImageSource? {
    database = database ?: getDatabase()
    val image = database?.transaction("blurredImageCache") {
        val result = objectStore("blurredImageCache").get(Key(localPath))
        if(result != undefined) result as Blob
        else null
    }
    return image?.let { ImageRaw(it) }
}

actual suspend fun blurAndCacheImage(
    cacheFileName: String,
    localPath: String,
    image: ImageSource,
    blurRadius: Float,
    overlayColor: Paint,
    quality: Float
): ImageSource? {
    database = database ?: getDatabase()

    // Determine the image src URL based on the ImageSource type
    val imageSrc: String = when (image) {
        is ImageResource -> {
            image.relativeUrl
        }
        is ImageRaw -> {
            createObjectURL(image.data as Blob)
        }
        else -> {
            // Fall back to loading from IndexedDB for user-uploaded images
            val splitDirectoryAndFileName = localPath.split("/")
            if (splitDirectoryAndFileName.size < 2) return null
            val directoryName = splitDirectoryAndFileName[0]
            val fileName = splitDirectoryAndFileName[1]
            val blob = database?.transaction("images") {
                val result = objectStore("images").get(Key("${directoryName}/${fileName}"))
                if (result != undefined) result as Blob
                else null
            } ?: return null
            createObjectURL(blob)
        }
    }

    return suspendCoroutine { continuation ->
        val imageElement = Image()
        imageElement.src = imageSrc

        imageElement.onload = {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

            // Downscale for performance and style
            val divisor = 10.0 - (quality * 8.0)
            canvas.width = (imageElement.width / divisor).toInt()
            canvas.height = (imageElement.height / divisor).toInt()

            val w = canvas.width.toDouble()
            val h = canvas.height.toDouble()

            // 1. Apply Blur
            ctx.filter = "blur(${blurRadius}px)"
            ctx.drawImage(imageElement, 0.0, 0.0, w, h)

            // 2. Blend the Radial Gradient Overlay
            applyRadialGradientOverlay(ctx, w, h, blurRadius, overlayColor)

            // Convert the canvas to a Blob and resume the coroutine
            canvas.toBlob({ blurredImageBlob ->
                if (blurredImageBlob != null) {
                    AppScope.launch {
                        database?.writeTransaction("blurredImageCache") {
                            val store = objectStore("blurredImageCache")
                            store.add(blurredImageBlob, Key(cacheFileName))
                        }
                        continuation.resume(ImageRaw(blurredImageBlob))
                    }
                } else {
                    continuation.resume(null)
                }
            }, "image/jpeg", quality.toDouble().coerceIn(0.0, 1.0))
        }

        imageElement.onerror = { _: Event, _: String, _: Int, _: Int, _: Any? ->
            continuation.resume(null)
        }
    }
}