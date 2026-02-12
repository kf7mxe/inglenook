package com.kf7mxe.inglenook.cache

import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.Paint

expect suspend fun blurServerImageAndCacheImage(location:String, image: ImageRemote,blurRadius: Float,overlayColor: Paint): ImageSource?
expect suspend fun getBlurredCachedImage(localPath:String):ImageSource?
expect suspend fun blurAndCacheImage(
    cacheFileName: String,
    localPath: String,
    image: ImageSource,
    blurRadius: Float,
    overlayColor: Paint,
    quality: Float = 0.5f // Added quality parameter
): ImageSource?
expect suspend fun clearImageCaches()
