package com.kf7mxe.inglenook.cache

import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageSource

expect suspend fun blurServerImageAndCacheImage(location:String, image: ImageRemote,blurRadius: Float): ImageSource?
expect suspend fun getBlurredCachedImage(localPath:String):ImageSource?
