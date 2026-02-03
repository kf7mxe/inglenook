package com.kf7mxe.inglenook.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception

actual suspend fun blurServerImageAndCacheImage(
    location: String,
    image: ImageRemote
): ImageSource? {
    val image =  fetch(image.url)
    val imageToBlur = image.blob()
    val bitmap = BitmapFactory.decodeByteArray(imageToBlur.data, 0, imageToBlur.data.size)

    val originalWidth = bitmap.width
    val originalHeight = bitmap.height

    // Define a maximum target dimension
    val maxDimension = 100  // Adjust this value based on your needs

    // Calculate scale factor while maintaining aspect ratio
    val scaleFactor = if (originalWidth > originalHeight) {
        maxDimension.toFloat() / originalWidth
    } else {
        maxDimension.toFloat() / originalHeight
    }

    // Ensure scaleFactor does not upscale images
    val finalScaleFactor = scaleFactor.coerceAtMost(1.0f)

    val newWidth = (originalWidth * finalScaleFactor).toInt()
    val newHeight = (originalHeight * finalScaleFactor).toInt()

    val resizedBitmap = bitmap.scale(newWidth, newHeight, false)

    val blurhashEncoded = BlurHash.encode(resizedBitmap, componentX = 5, componentY = 4)
    val blurredBitmap = BlurHash.decode(
        blurHash = blurhashEncoded,
        width = bitmap.width / 4,
        height = bitmap.height / 4,
    )
    val blurredCachedImagesDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
    if(!blurredCachedImagesDirectory.exists()) {
        blurredCachedImagesDirectory.mkdirs()
    }
    val savedImageFile = File(blurredCachedImagesDirectory, location)
    var blob:Blob? = null
    try {
        val byteArrayOutputStream = ByteArrayOutputStream()
        blurredBitmap?.compress(Bitmap.CompressFormat.JPEG,70, byteArrayOutputStream )
        val fileOutputStream = FileOutputStream(savedImageFile)
        val byteArray = byteArrayOutputStream.toByteArray()



        fileOutputStream.write(byteArray)
        fileOutputStream.flush()
        fileOutputStream.close()
        val mimeType = imageToBlur.type.takeIf { it.isNotEmpty() } ?: "image/jpeg"
        blob = Blob(
            byteArrayOutputStream.toByteArray(),
            type = mimeType
        )
        byteArrayOutputStream.close()
    }
    catch (e: Exception) {
        println("error reading image from storage ${e.message}")

    }
    return blob?.let { ImageRaw(it) }
}

actual suspend fun getBlurredCachedImage(localPath: String): ImageSource? {
    val splitDirectoryAndFileName = localPath.split("/")
    val directory = splitDirectoryAndFileName[0]
    val fileName = splitDirectoryAndFileName[1]
    val blurredCachedImagsDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
    val blurredImage = File(blurredCachedImagsDirectory, fileName)
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