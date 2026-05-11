package com.kf7mxe.inglenook.util

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.url.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun loadResizedImagePixels(
    file: FileReference,
    maxWidth: Int,
    maxHeight: Int
): ImageData {
    return suspendCancellableCoroutine { continuation ->
        val img = document.createElement("img") as HTMLImageElement
        val objectUrl = URL.createObjectURL(file)

        img.onload = {
            URL.revokeObjectURL(objectUrl)
            try {
                var width = img.width
                var height = img.height

                if (width > maxWidth || height > maxHeight) {
                    val aspectRatio = width.toDouble() / height.toDouble()
                    if (width > height) {
                        width = maxWidth
                        height = (maxWidth / aspectRatio).toInt()
                    } else {
                        height = maxHeight
                        width = (maxHeight * aspectRatio).toInt()
                    }
                }

                val canvas = document.createElement("canvas") as HTMLCanvasElement
                canvas.width = width
                canvas.height = height

                val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
                ctx.drawImage(img, 0.0, 0.0, width.toDouble(), height.toDouble())

                val imageData = ctx.getImageData(0.0, 0.0, width.toDouble(), height.toDouble())
                val data = imageData.data.asDynamic()

                val pixels = FloatArray(width * height * 3)
                for (i in 0 until width * height) {
                    pixels[i * 3] = (data[i * 4] as Int).toFloat() / 255f
                    pixels[i * 3 + 1] = (data[i * 4 + 1] as Int).toFloat() / 255f
                    pixels[i * 3 + 2] = (data[i * 4 + 2] as Int).toFloat() / 255f
                }
                continuation.resume(ImageData(pixels, width, height))
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
            Unit
        }

        img.onerror = { _, _, _, _, _ ->
            URL.revokeObjectURL(objectUrl)
            continuation.resumeWithException(Exception("Failed to load image"))
            Unit
        }

        img.src = objectUrl
    }
}

actual suspend fun loadResizedImagePixels(
    resource: ImageResource,
    maxWidth: Int,
    maxHeight: Int
): ImageData {
    return suspendCancellableCoroutine { continuation ->
        val img = document.createElement("img") as HTMLImageElement

        img.onload = {
            try {
                var width = img.width
                var height = img.height

                if (width > maxWidth || height > maxHeight) {
                    val aspectRatio = width.toDouble() / height.toDouble()
                    if (width > height) {
                        width = maxWidth
                        height = (maxWidth / aspectRatio).toInt()
                    } else {
                        height = maxHeight
                        width = (maxHeight * aspectRatio).toInt()
                    }
                }

                val canvas = document.createElement("canvas") as HTMLCanvasElement
                canvas.width = width
                canvas.height = height

                val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
                ctx.drawImage(img, 0.0, 0.0, width.toDouble(), height.toDouble())

                val imageData = ctx.getImageData(0.0, 0.0, width.toDouble(), height.toDouble())
                val data = imageData.data.asDynamic()

                val pixels = FloatArray(width * height * 3)
                for (i in 0 until width * height) {
                    pixels[i * 3] = (data[i * 4] as Int).toFloat() / 255f
                    pixels[i * 3 + 1] = (data[i * 4 + 1] as Int).toFloat() / 255f
                    pixels[i * 3 + 2] = (data[i * 4 + 2] as Int).toFloat() / 255f
                }
                continuation.resume(ImageData(pixels, width, height))
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
            Unit
        }

        img.onerror = { _, _, _, _, _ ->
            continuation.resumeWithException(Exception("Failed to load image resource"))
            Unit
        }

        img.src = resource.relativeUrl
    }
}

actual suspend fun loadResizedImagePixels(
    imageSource: ImageSource,
    maxWidth: Int,
    maxHeight: Int
): ImageData? {
    val blob = (imageSource as? ImageRaw)?.data ?: return null
    return suspendCancellableCoroutine { continuation ->
        val img = document.createElement("img") as HTMLImageElement
        val objectUrl = URL.createObjectURL(blob)

        img.onload = {
            URL.revokeObjectURL(objectUrl)
            try {
                var width = img.width
                var height = img.height

                if (width > maxWidth || height > maxHeight) {
                    val aspectRatio = width.toDouble() / height.toDouble()
                    if (width > height) {
                        width = maxWidth
                        height = (maxWidth / aspectRatio).toInt()
                    } else {
                        height = maxHeight
                        width = (maxHeight * aspectRatio).toInt()
                    }
                }

                val canvas = document.createElement("canvas") as HTMLCanvasElement
                canvas.width = width
                canvas.height = height

                val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
                ctx.drawImage(img, 0.0, 0.0, width.toDouble(), height.toDouble())

                val imageData = ctx.getImageData(0.0, 0.0, width.toDouble(), height.toDouble())
                val data = imageData.data.asDynamic()

                val pixels = FloatArray(width * height * 3)
                for (i in 0 until width * height) {
                    pixels[i * 3] = (data[i * 4] as Int).toFloat() / 255f
                    pixels[i * 3 + 1] = (data[i * 4 + 1] as Int).toFloat() / 255f
                    pixels[i * 3 + 2] = (data[i * 4 + 2] as Int).toFloat() / 255f
                }
                continuation.resume(ImageData(pixels, width, height))
            } catch (e: Exception) {
                continuation.resume(null)
            }
            Unit
        }

        img.onerror = { _, _, _, _, _ ->
            URL.revokeObjectURL(objectUrl)
            continuation.resume(null)
            Unit
        }

        img.src = objectUrl
    }
}

actual suspend fun loadResizedImagePixelsFromUrl(
    url: String,
    maxWidth: Int,
    maxHeight: Int
): ImageData? {
    return suspendCancellableCoroutine { continuation ->
        val img = document.createElement("img") as HTMLImageElement
        img.setAttribute("crossorigin", "anonymous")

        img.onload = {
            try {
                var width = img.width
                var height = img.height

                if (width > maxWidth || height > maxHeight) {
                    val aspectRatio = width.toDouble() / height.toDouble()
                    if (width > height) {
                        width = maxWidth
                        height = (maxWidth / aspectRatio).toInt()
                    } else {
                        height = maxHeight
                        width = (maxHeight * aspectRatio).toInt()
                    }
                }

                val canvas = document.createElement("canvas") as HTMLCanvasElement
                canvas.width = width
                canvas.height = height

                val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
                ctx.drawImage(img, 0.0, 0.0, width.toDouble(), height.toDouble())

                val imageData = ctx.getImageData(0.0, 0.0, width.toDouble(), height.toDouble())
                val data = imageData.data.asDynamic()

                val pixels = FloatArray(width * height * 3)
                for (i in 0 until width * height) {
                    pixels[i * 3] = (data[i * 4] as Int).toFloat() / 255f
                    pixels[i * 3 + 1] = (data[i * 4 + 1] as Int).toFloat() / 255f
                    pixels[i * 3 + 2] = (data[i * 4 + 2] as Int).toFloat() / 255f
                }
                continuation.resume(ImageData(pixels, width, height))
            } catch (e: Exception) {
                continuation.resume(null)
            }
            Unit
        }

        img.onerror = { _, _, _, _, _ ->
            continuation.resume(null)
            Unit
        }

        img.src = url
    }
}
