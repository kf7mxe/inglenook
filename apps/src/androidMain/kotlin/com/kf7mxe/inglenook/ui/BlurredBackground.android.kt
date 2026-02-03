package com.kf7mxe.inglenook.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import androidx.core.graphics.scale
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.views.AndroidAppContext
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of blurred background.
 * Uses RenderEffect on Android 12+ for real blur, falls back to alpha overlay on older versions.
 */
actual fun ViewWriter.blurredImage(
    imageSource: Reactive<ImageSource?>,
    blurRadius: Float
) {
    image {
        this@image.rView::shown {
            imageSource() != null
        }
        ::source {
            imageSource()
        }
        scaleType = ImageScaleType.Crop

        // Apply blur effect on Android 12+
        val imageView = rView.native as? ImageView
        if (imageView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                imageView.setRenderEffect(blurEffect)
            } catch (e: Exception) {
                // Fallback: just show the image with reduced alpha
                imageView.alpha = 0.3f
            }
        } else if (imageView != null) {
            // Fallback for older Android: show with reduced alpha as "frosted" effect
            imageView.alpha = 0.3f
        }
    }
}



//actual suspend fun blurAndCacheImage(localPath: String, image: ImageSource):ImageSource? {
//    return withContext(Dispatchers.IO) {
//        val splitDirectoryAndFileName = localPath.split("/")
//        val directory = splitDirectoryAndFileName[0]
//        val fileName = splitDirectoryAndFileName[1]
////    val imageToBlur = File(AndroidAppContext.applicationCtx.filesDir,localPath)
//
//        val imageToBlurInputStream = (image as ImageRaw).data
//        val bitmap = BitmapFactory.decodeByteArray(imageToBlurInputStream.data, 0, imageToBlurInputStream.data.size)
//
//        val originalWidth = bitmap.width
//        val originalHeight = bitmap.height
//
//        // Define a maximum target dimension
//        val maxDimension = 100  // Adjust this value based on your needs
//
//        // Calculate scale factor while maintaining aspect ratio
//        val scaleFactor = if (originalWidth > originalHeight) {
//            maxDimension.toFloat() / originalWidth
//        } else {
//            maxDimension.toFloat() / originalHeight
//        }
//
//        // Ensure scaleFactor does not upscale images
//        val finalScaleFactor = scaleFactor.coerceAtMost(1.0f)
//
//        val newWidth = (originalWidth * finalScaleFactor).toInt()
//        val newHeight = (originalHeight * finalScaleFactor).toInt()
//
//        val resizedBitmap = bitmap.scale(newWidth, newHeight, false)
//
//        val blurhashEncoded = BlurHash.encode(resizedBitmap, componentX = 5, componentY = 4)
//        val blurredBitmap = BlurHash.decode(
//            blurHash = blurhashEncoded,
//            width = bitmap.width / 4,
//            height = bitmap.height / 4,
//        )
//        val blurredCachedImagsDirectory = File(AndroidAppContext.applicationCtx.cacheDir, "blurredImages")
//        if (!blurredCachedImagsDirectory.exists()) {
//            blurredCachedImagsDirectory.mkdirs()
//        }
//        val savedImageFile = File(blurredCachedImagsDirectory, fileName)
//        var blob: Blob? = null
//        try {
//            val byteArrayOutputStream = ByteArrayOutputStream()
//            blurredBitmap?.compress(Bitmap.CompressFormat.JPEG, 30, byteArrayOutputStream)
//            val fileOutputStream = FileOutputStream(savedImageFile)
//            val byteArray = byteArrayOutputStream.toByteArray()
//            fileOutputStream.write(byteArray)
//            fileOutputStream.flush()
//            fileOutputStream.close()
//            blob = Blob(
//                byteArrayOutputStream.toByteArray(),
//                type = "image/jpeg"
//            )
//            byteArrayOutputStream.close()
//        } catch (e: java.lang.Exception) {
//
//        }
//        blob?.let { ImageRaw(it) }
//    }
//}