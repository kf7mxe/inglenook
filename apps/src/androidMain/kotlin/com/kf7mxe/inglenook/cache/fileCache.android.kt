package com.kf7mxe.inglenook.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.graphics.scale
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.views.AndroidAppContext
import com.vanniktech.blurhash.BlurHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception

actual suspend fun blurServerImageAndCacheImage(
    location: String,
    image: ImageRemote,
    blurRadius: Float,
    overlayColor: Paint
): ImageSource? {
    val fetchedImage = fetch(image.url)
    val imageBlob = fetchedImage.blob()
    val bitmap = BitmapFactory.decodeByteArray(imageBlob.data, 0, imageBlob.data.size)

    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    val maxDimension = 100

    val scaleFactor = if (originalWidth > originalHeight) {
        maxDimension.toFloat() / originalWidth
    } else {
        maxDimension.toFloat() / originalHeight
    }

    val finalScaleFactor = scaleFactor.coerceAtMost(1.0f)
    val newWidth = (originalWidth * finalScaleFactor).toInt()
    val newHeight = (originalHeight * finalScaleFactor).toInt()

    val resizedBitmap = bitmap.scale(newWidth, newHeight, false)

    // 1. Apply Blur
    val blurredBitmap = fastBlur(resizedBitmap, blurRadius.toInt())

    // 2. Blend the Color Overlay (Updated to Radial Gradient)
    if (blurredBitmap != null) {
        val canvas = Canvas(blurredBitmap)
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        // Parse the Hex color
        val overlayColorInt = overlayColor.closestColor().toInt()

        // Calculate Alpha based on blur radius
        val maxAlpha = 150
        val calculatedAlpha = ((blurRadius / 20f) * 255).toInt().coerceIn(0, maxAlpha)

        // Create the "Edge Color" by combining the overlay color with the calculated alpha
        val finalEdgeColor = (overlayColorInt and 0x00FFFFFF) or (calculatedAlpha shl 24)

        // Calculate radius (distance from center to corner to ensure full coverage)
        val radius = kotlin.math.hypot(w / 2.0, h / 2.0).toFloat()

        // Create Radial Gradient: Center is Transparent, Edge is your colored overlay
        val gradient = android.graphics.RadialGradient(
            w / 2f, h / 2f,      // Center X, Y
            radius,              // Radius
            intArrayOf(android.graphics.Color.TRANSPARENT, finalEdgeColor), // Colors
            null,                // Positions (null = evenly spaced)
            android.graphics.Shader.TileMode.CLAMP
        )

        val paint = android.graphics.Paint()
        paint.shader = gradient
        paint.isDither = true // smoothens the gradient banding

        // Draw the gradient over the whole image
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    // 3. Save to Cache
    val blurredCachedImagesDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
    if (!blurredCachedImagesDirectory.exists()) {
        blurredCachedImagesDirectory.mkdirs()
    }
    val savedImageFile = File(blurredCachedImagesDirectory, location)
    var blob: Blob? = null
    try {
        val byteArrayOutputStream = ByteArrayOutputStream()
        blurredBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val fileOutputStream = FileOutputStream(savedImageFile)
        val byteArray = byteArrayOutputStream.toByteArray()

        fileOutputStream.write(byteArray)
        fileOutputStream.flush()
        fileOutputStream.close()

        val mimeType = imageBlob.type.takeIf { it.isNotEmpty() } ?: "image/jpeg"
        blob = Blob(
            byteArrayOutputStream.toByteArray(),
            type = mimeType
        )
        byteArrayOutputStream.close()
    } catch (e: Exception) {
        println("error reading image from storage ${e.message}")
    }

    // Cleanup
    bitmap.recycle()
    if (resizedBitmap != bitmap) resizedBitmap.recycle()

    return blob?.let { ImageRaw(it) }
}

/**
 * A standard Stack Blur algorithm implementation.
 * This does not require RenderScript (deprecated) or API 31+ (RenderEffect).
 */
/**
 * A standard Stack Blur algorithm implementation.
 * Fixed: Uses 'var' for mutable variables and handles nullable Bitmap config.
 */
fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap? {
    // FIX 1: Handle nullable config. Use ARGB_8888 as a safe default.
    val config = sentBitmap.config ?: Bitmap.Config.ARGB_8888

    // FIX 2: Ensure copy is successful. copy() can return null on OOM.
    val bitmap = sentBitmap.copy(config, true) ?: return null

    // FIX 3: If radius is 0, return the un-blurred copy instead of null.
    // This prevents the entire chain from failing just because the blur is subtle.
    if (radius < 1) {
        return bitmap
    }

    val w = bitmap.width
    val h = bitmap.height

    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)

    var rsum: Int
    var gsum: Int
    var bsum: Int
    var p: Int
    var yp: Int
    var yi: Int
    val vmin = IntArray(maxOf(w, h))

    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (i in 0 until 256 * divsum) {
        dv[i] = (i / divsum)
    }

    var ywI = 0
    var yiI = 0

    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int
    var stackstart: Int
    var sir: IntArray
    var rbs: Int
    val r1 = radius + 1
    var routsum: Int
    var goutsum: Int
    var boutsum: Int
    var rinsum: Int
    var ginsum: Int
    var binsum: Int

    // Horizontal Pass
    for (y in 0 until h) {
        rinsum = 0
        ginsum = 0
        binsum = 0
        routsum = 0
        goutsum = 0
        boutsum = 0
        rsum = 0
        gsum = 0
        bsum = 0

        for (i in -radius..radius) {
            p = pix[yiI + minOf(wm, maxOf(i, 0))]
            sir = stack[i + radius]
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = (p and 0x0000ff)
            rbs = r1 - Math.abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
        }
        stackpointer = radius

        for (x in 0 until w) {
            r[yiI] = dv[rsum]
            g[yiI] = dv[gsum]
            b[yiI] = dv[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]

            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]

            if (y == 0) {
                vmin[x] = minOf(x + radius + 1, wm)
            }
            p = pix[ywI + vmin[x]]

            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = (p and 0x0000ff)

            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackpointer = (stackpointer + 1) % div
            sir = stack[(stackpointer) % div]

            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]

            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]

            yiI++
        }
        ywI += w
    }

    // Vertical Pass
    for (x in 0 until w) {
        rinsum = 0
        ginsum = 0
        binsum = 0
        routsum = 0
        goutsum = 0
        boutsum = 0
        rsum = 0
        gsum = 0
        bsum = 0
        yp = -radius * w

        for (i in -radius..radius) {
            yiI = maxOf(0, yp) + x

            sir = stack[i + radius]

            sir[0] = r[yiI]
            sir[1] = g[yiI]
            sir[2] = b[yiI]

            rbs = r1 - Math.abs(i)

            rsum += r[yiI] * rbs
            gsum += g[yiI] * rbs
            bsum += b[yiI] * rbs

            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }

            if (i < hm) {
                yp += w
            }
        }
        yiI = x
        stackpointer = radius
        for (y in 0 until h) {
            // Preserve alpha channel
            pix[yiI] = (0xff000000.toInt() or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]

            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]

            if (x == 0) {
                vmin[y] = minOf(y + r1, hm) * w
            }
            p = x + vmin[y]

            sir[0] = r[p]
            sir[1] = g[p]
            sir[2] = b[p]

            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer]

            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]

            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]

            yiI += w
        }
    }

    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return bitmap
}

actual suspend fun clearImageCaches() {
    withContext(Dispatchers.IO) {
        val blurredCachedImagesDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
        if (blurredCachedImagesDirectory.exists()) {
            blurredCachedImagesDirectory.deleteRecursively()
        }
    }
}

actual suspend fun getBlurredCachedImage(localPath: String): ImageSource? {
    println("DEBUG localPath ${localPath}")
//    val directory = splitDirectoryAndFileName[0]
//    val fileName = splitDirectoryAndFileName[1]
    println("DEBUG fileName $localPath")
    val blurredCachedImagsDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
    val blurredImage = File(blurredCachedImagsDirectory, localPath)
    if(blurredImage.exists()) {
        try {
            val byteArray = withContext(Dispatchers.IO) {
                FileInputStream(blurredImage).use {
                    it.readBytes()
                }
            }
            val blob = Blob(byteArray, "image/jpeg")
            return blob.let { ImageRaw(it) }
        } catch (e: Exception) {
            println("error reading image from storage")
            e.printStackTrace()
        }
    }
    return null
}

actual suspend fun blurAndCacheImage(
    cacheFileName: String,
    localPath: String,
    image: ImageSource,
    blurRadius: Float,
    overlayColor: Paint,
    quality: Float
): ImageSource? {
    println("DEBUG blurAndCacheImage Android localPath: $localPath cacheFileName: $cacheFileName")

    return withContext(Dispatchers.IO) {
        try {
            // 1. Handle File Names
            val splitDirectoryAndFileName = localPath.split("/")
            val (directoryName, fileName) = if (splitDirectoryAndFileName.size > 1) {
                splitDirectoryAndFileName[0] to splitDirectoryAndFileName[1]
            } else {
                "" to localPath
            }

            println("Processing: Directory='$directoryName', FileName='$fileName'")

            // 2. Decode Image Data
            val imageRaw = image as? ImageRaw
            if (imageRaw == null) {
                println("Error: ImageSource is not ImageRaw")
                return@withContext null
            }

            val imageData = imageRaw.data

            // FIX: Ensure bitmap is Mutable. Canvas drawing (Step 5) requires a Mutable bitmap.
            val options = BitmapFactory.Options().apply {
                inMutable = true
            }

            val bitmap = BitmapFactory.decodeByteArray(
                imageData.data,
                0,
                imageData.data.size,
                options
            ) ?: run {
                println("Error: Failed to decode bitmap. Input size: ${imageData.data.size}")
                return@withContext null
            }

            // 3. Resize
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // Map quality 0.0-1.0 to a max dimension (e.g., 50px to 500px)
            val maxDimension = (50 + (quality * 450)).toInt()

            val scaleFactor = if (originalWidth > originalHeight) {
                maxDimension.toFloat() / originalWidth
            } else {
                maxDimension.toFloat() / originalHeight
            }

            val finalScaleFactor = scaleFactor.coerceAtMost(1.0f)
            val newWidth = (originalWidth * finalScaleFactor).toInt().coerceAtLeast(1)
            val newHeight = (originalHeight * finalScaleFactor).toInt().coerceAtLeast(1)

            // Note: scale() might return the same object if dimensions match
            val resizedBitmap = if (finalScaleFactor < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            // 4. Apply Blur
            // Ensure fastBlur handles the bitmap correctly.
            // If fastBlur returns null, we need to catch it here.
            val blurredBitmap = fastBlur(resizedBitmap, blurRadius.toInt())

            if (blurredBitmap == null) {
                println("Error: fastBlur returned null")
                // Clean up previous bitmaps before returning
                if (bitmap != resizedBitmap) resizedBitmap.recycle()
                bitmap.recycle()
                return@withContext null
            }

            // 5. Apply Radial Gradient Overlay
            // Wrapped in try-catch specifically for Canvas operations
            try {
                val canvas = Canvas(blurredBitmap) // Requires mutable bitmap
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()

                val maxAlpha = 153
                val calculatedAlpha = ((blurRadius / 20f) * 255).toInt().coerceIn(0, maxAlpha)

                val overlayColorInt = overlayColor.closestColor().toInt()
                val finalEdgeColor = (overlayColorInt and 0x00FFFFFF) or (calculatedAlpha shl 24)

                val radius = kotlin.math.hypot(w / 2.0, h / 2.0).toFloat()

                val gradient = android.graphics.RadialGradient(
                    w / 2f, h / 2f,
                    radius,
                    intArrayOf(android.graphics.Color.TRANSPARENT, finalEdgeColor),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )

                val paint = android.graphics.Paint()
                paint.shader = gradient
                paint.isDither = true

                canvas.drawRect(0f, 0f, w, h, paint)
            } catch (e: Exception) {
                println("Error applying gradient overlay: ${e.message}")
                e.printStackTrace()
                // Proceeding without overlay is usually better than crashing
            }

            // 6. Save to Cache
            val blurredCachedImagesDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
            if (!blurredCachedImagesDirectory.exists()) {
                blurredCachedImagesDirectory.mkdirs()
            }

            val savedImageFile = File(blurredCachedImagesDirectory, cacheFileName)
            var resultImageSource: ImageSource? = null

            ByteArrayOutputStream().use { byteArrayOutputStream ->
                // Compress
                // blurredBitmap is guaranteed non-null here
                val compressionQuality = (quality * 100).toInt().coerceIn(10, 100)
                val success = blurredBitmap.compress(Bitmap.CompressFormat.JPEG, compressionQuality, byteArrayOutputStream)

                if (success) {
                    val byteArray = byteArrayOutputStream.toByteArray()

                    if (byteArray.isNotEmpty()) {
                        FileOutputStream(savedImageFile).use { fos ->
                            fos.write(byteArray)
                            fos.flush()
                        }
                        println("Saved blurred image to cache: ${savedImageFile.absolutePath}")

                        val blob = Blob(byteArray, type = "image/jpeg")
                        resultImageSource = ImageRaw(blob)
                    } else {
                        println("Error: Compressed byte array is empty")
                    }
                } else {
                    println("Error: Bitmap.compress returned false. Bitmap config: ${blurredBitmap.config}, w: ${blurredBitmap.width}, h: ${blurredBitmap.height}")
                }
            }

            // 7. Cleanup
            // Only recycle intermediate bitmaps.
            // If resizedBitmap is a new instance, recycle it.
            if (bitmap != resizedBitmap) {
                resizedBitmap.recycle()
            }
            // Always recycle original source bitmap
            bitmap.recycle()

            // If blurredBitmap is distinct from resized (it usually is), recycle it now that we have the stream
            if (blurredBitmap != resizedBitmap && blurredBitmap != bitmap) {
                blurredBitmap.recycle()
            }

            return@withContext resultImageSource

        } catch (e: Exception) {
            println("CRITICAL ERROR in blurAndCacheImage: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }
}