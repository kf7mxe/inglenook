package com.kf7mxe.inglenook.cache

import com.juul.indexeddb.Key
import com.kf7mxe.inglenook.database
import com.kf7mxe.inglenook.getDatabase
import com.kf7mxe.inglenook.storage.getCacheFileName
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
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

actual suspend fun blurServerImageAndCacheImage(
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
            ctx.filter = "none" // Turn off blur for the gradient drawing
            ctx.globalCompositeOperation = "source-over"

            // Calculate geometry
            val cx = w / 2.0
            val cy = h / 2.0
            val radius = kotlin.math.sqrt(cx * cx + cy * cy) // Radius to corner

            val gradient = ctx.createRadialGradient(cx, cy, 0.0, cx, cy, radius)
            val hexColor = overlayColor.closestColor().toWeb()

            // Stop 0 (Center): Transparent
            gradient.addColorStop(0.0, "rgba(0,0,0,0)")
            // Stop 1 (Edge): Solid color (Opacity controlled by globalAlpha below)
            gradient.addColorStop(1.0, hexColor)

            // Logic: The higher the blurRadius, the stronger the overlay color.
            // We use globalAlpha to control the max opacity of the gradient edges.
            val calculatedAlpha = (blurRadius / 20.0).coerceIn(0.0, 0.6)

            ctx.globalAlpha = calculatedAlpha
            ctx.fillStyle = gradient
            ctx.fillRect(0.0, 0.0, w, h)

            // 3. Convert to Blob
            canvas.toBlob({ blurredImageBlob ->
                if (blurredImageBlob != null) {
                    AppScope.launch {
                        database?.writeTransaction("blurredImageCache") {
                            val store = objectStore("blurredImageCache")
                            store.add(blurredImageBlob, Key(getCacheFileName(location)))
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
        val result = objectStore("blurredImageCache").get(Key(getCacheFileName(localPath)))
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
    val splitDirectoryAndFileName = localPath.split("/")
    val directoryName = splitDirectoryAndFileName[0]
    val fileName = splitDirectoryAndFileName[1]
    val image = database?.transaction("images") {
        val result = objectStore("images").get(Key("${directoryName}/${fileName}"))
        if (result != undefined) result as Blob
        else null
    }
    if (image == null) return null

    return suspendCoroutine { continuation ->
        val imageElement = Image()
        val objectUrl = createObjectURL(image)
        imageElement.src = objectUrl
        imageElement.crossOrigin = "Anonymous"

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
            ctx.filter = "none" // Turn off blur for the gradient drawing
            ctx.globalCompositeOperation = "source-over"

            // Calculate geometry
            val cx = w / 2.0
            val cy = h / 2.0
            val radius = kotlin.math.sqrt(cx * cx + cy * cy) // Radius to corner

            val gradient = ctx.createRadialGradient(cx, cy, 0.0, cx, cy, radius)
            val hexColor = overlayColor.closestColor().toWeb()

            // Stop 0 (Center): Transparent
            gradient.addColorStop(0.0, "rgba(0,0,0,0)")
            // Stop 1 (Edge): Solid color (Opacity controlled by globalAlpha below)
            gradient.addColorStop(1.0, hexColor)

            // Logic: The higher the blurRadius, the stronger the overlay color.
            // We use globalAlpha to control the max opacity of the gradient edges.
            val calculatedAlpha = (blurRadius / 20.0).coerceIn(0.0, 0.6)

            ctx.globalAlpha = calculatedAlpha
            ctx.fillStyle = gradient
            ctx.fillRect(0.0, 0.0, w, h)
            // Convert the canvas to a Blob and resume the coroutine
            canvas.toBlob({ blurredImageBlob ->
                if (blurredImageBlob != null) {
                    AppScope.launch {
                        database?.writeTransaction("blurredImageCache") {
                            val store = objectStore("blurredImageCache")
                            store.add(blurredImageBlob, Key(cacheFileName))
                        }
                        continuation.resume(blurredImageBlob?.let {ImageRaw(it)}) // Resume coroutine with result
                    }
                } else {
                    continuation.resume(null) // Resume with null if conversion fails
                }
            }, "image/jpeg",quality.toDouble().coerceIn(0.0,1.0))
        }

        imageElement.onerror = { _: Event, _: String, _: Int, _: Int, _: Any? ->
            continuation.resume(null)
        }
    }
    return null
}