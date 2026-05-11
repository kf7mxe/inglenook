package com.kf7mxe.inglenook.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun loadResizedImagePixels(
    file: FileReference,
    maxWidth: Int,
    maxHeight: Int
): ImageData = withContext(Dispatchers.IO) {
    // 1. Decode bounds only
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    AndroidAppContext.applicationCtx.contentResolver.openInputStream(file.uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }

    // 2. Calculate inSampleSize
    var inSampleSize = 1
    if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
        val halfHeight = options.outHeight / 2
        val halfWidth = options.outWidth / 2
        while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
            inSampleSize *= 2
        }
    }

    // 3. Decode with inSampleSize
    options.inJustDecodeBounds = false
    options.inSampleSize = inSampleSize

    val sampledBitmap = AndroidAppContext.applicationCtx.contentResolver.openInputStream(file.uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw Exception("Failed to decode bitmap")

    // 4. Scale to exact dimensions if needed
    val width = sampledBitmap.width
    val height = sampledBitmap.height

    var finalWidth = width
    var finalHeight = height

    if (width > maxWidth || height > maxHeight) {
        val aspectRatio = width.toDouble() / height.toDouble()
        if (width > height) {
            finalWidth = maxWidth
            finalHeight = (maxWidth / aspectRatio).toInt()
        } else {
            finalHeight = maxHeight
            finalWidth = (maxHeight * aspectRatio).toInt()
        }
    }

    val finalBitmap = if (width != finalWidth || height != finalHeight) {
        val scaled = Bitmap.createScaledBitmap(sampledBitmap, finalWidth, finalHeight, true)
        if (scaled != sampledBitmap) sampledBitmap.recycle()
        scaled
    } else {
        sampledBitmap
    }

    // 5. Extract pixels
    val finalW = finalBitmap.width
    val finalH = finalBitmap.height
    val intPixels = IntArray(finalW * finalH)
    finalBitmap.getPixels(intPixels, 0, finalW, 0, 0, finalW, finalH)

    val floatPixels = FloatArray(finalW * finalH * 3)
    for (i in intPixels.indices) {
        val color = intPixels[i]
        floatPixels[i * 3] = Color.red(color) / 255f
        floatPixels[i * 3 + 1] = Color.green(color) / 255f
        floatPixels[i * 3 + 2] = Color.blue(color) / 255f
    }

    finalBitmap.recycle()

    ImageData(floatPixels, finalW, finalH)
}

actual suspend fun loadResizedImagePixels(
    resource: ImageResource,
    maxWidth: Int,
    maxHeight: Int
): ImageData = withContext(Dispatchers.IO) {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeResource(AndroidAppContext.applicationCtx.resources, resource.resource, options)

    var inSampleSize = 1
    if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
        val halfHeight = options.outHeight / 2
        val halfWidth = options.outWidth / 2
        while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
            inSampleSize *= 2
        }
    }

    options.inJustDecodeBounds = false
    options.inSampleSize = inSampleSize

    val sampledBitmap = BitmapFactory.decodeResource(
        AndroidAppContext.applicationCtx.resources, resource.resource, options
    ) ?: throw Exception("Failed to decode resource bitmap")

    val width = sampledBitmap.width
    val height = sampledBitmap.height
    var finalWidth = width
    var finalHeight = height

    if (width > maxWidth || height > maxHeight) {
        val aspectRatio = width.toDouble() / height.toDouble()
        if (width > height) {
            finalWidth = maxWidth
            finalHeight = (maxWidth / aspectRatio).toInt()
        } else {
            finalHeight = maxHeight
            finalWidth = (maxHeight * aspectRatio).toInt()
        }
    }

    val finalBitmap = if (width != finalWidth || height != finalHeight) {
        val scaled = Bitmap.createScaledBitmap(sampledBitmap, finalWidth, finalHeight, true)
        if (scaled != sampledBitmap) sampledBitmap.recycle()
        scaled
    } else {
        sampledBitmap
    }

    val finalW = finalBitmap.width
    val finalH = finalBitmap.height
    val intPixels = IntArray(finalW * finalH)
    finalBitmap.getPixels(intPixels, 0, finalW, 0, 0, finalW, finalH)

    val floatPixels = FloatArray(finalW * finalH * 3)
    for (i in intPixels.indices) {
        val color = intPixels[i]
        floatPixels[i * 3] = Color.red(color) / 255f
        floatPixels[i * 3 + 1] = Color.green(color) / 255f
        floatPixels[i * 3 + 2] = Color.blue(color) / 255f
    }

    finalBitmap.recycle()

    ImageData(floatPixels, finalW, finalH)
}

actual suspend fun loadResizedImagePixels(
    imageSource: ImageSource,
    maxWidth: Int,
    maxHeight: Int
): ImageData? = withContext(Dispatchers.IO) {
    try {
        val bytes = (imageSource as? ImageRaw)?.data?.data ?: return@withContext null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var inSampleSize = 1
        if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize

        val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return@withContext null

        val width = sampledBitmap.width
        val height = sampledBitmap.height
        var finalWidth = width
        var finalHeight = height

        if (width > maxWidth || height > maxHeight) {
            val aspectRatio = width.toDouble() / height.toDouble()
            if (width > height) {
                finalWidth = maxWidth
                finalHeight = (maxWidth / aspectRatio).toInt()
            } else {
                finalHeight = maxHeight
                finalWidth = (maxHeight * aspectRatio).toInt()
            }
        }

        val finalBitmap = if (width != finalWidth || height != finalHeight) {
            val scaled = Bitmap.createScaledBitmap(sampledBitmap, finalWidth, finalHeight, true)
            if (scaled != sampledBitmap) sampledBitmap.recycle()
            scaled
        } else {
            sampledBitmap
        }

        val finalW = finalBitmap.width
        val finalH = finalBitmap.height
        val intPixels = IntArray(finalW * finalH)
        finalBitmap.getPixels(intPixels, 0, finalW, 0, 0, finalW, finalH)

        val floatPixels = FloatArray(finalW * finalH * 3)
        for (i in intPixels.indices) {
            val color = intPixels[i]
            floatPixels[i * 3] = Color.red(color) / 255f
            floatPixels[i * 3 + 1] = Color.green(color) / 255f
            floatPixels[i * 3 + 2] = Color.blue(color) / 255f
        }

        finalBitmap.recycle()

        ImageData(floatPixels, finalW, finalH)
    } catch (e: Exception) {
        null
    }
}

actual suspend fun loadResizedImagePixelsFromUrl(
    url: String,
    maxWidth: Int,
    maxHeight: Int
): ImageData? = withContext(Dispatchers.IO) {
    try {
        val bytes = fetch(url).blob().data

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var inSampleSize = 1
        if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize

        val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return@withContext null

        val width = sampledBitmap.width
        val height = sampledBitmap.height
        var finalWidth = width
        var finalHeight = height

        if (width > maxWidth || height > maxHeight) {
            val aspectRatio = width.toDouble() / height.toDouble()
            if (width > height) {
                finalWidth = maxWidth
                finalHeight = (maxWidth / aspectRatio).toInt()
            } else {
                finalHeight = maxHeight
                finalWidth = (maxHeight * aspectRatio).toInt()
            }
        }

        val finalBitmap = if (width != finalWidth || height != finalHeight) {
            val scaled = Bitmap.createScaledBitmap(sampledBitmap, finalWidth, finalHeight, true)
            if (scaled != sampledBitmap) sampledBitmap.recycle()
            scaled
        } else {
            sampledBitmap
        }

        val finalW = finalBitmap.width
        val finalH = finalBitmap.height
        val intPixels = IntArray(finalW * finalH)
        finalBitmap.getPixels(intPixels, 0, finalW, 0, 0, finalW, finalH)

        val floatPixels = FloatArray(finalW * finalH * 3)
        for (i in intPixels.indices) {
            val color = intPixels[i]
            floatPixels[i * 3] = Color.red(color) / 255f
            floatPixels[i * 3 + 1] = Color.green(color) / 255f
            floatPixels[i * 3 + 2] = Color.blue(color) / 255f
        }

        finalBitmap.recycle()

        ImageData(floatPixels, finalW, finalH)
    } catch (e: Exception) {
        null
    }
}
