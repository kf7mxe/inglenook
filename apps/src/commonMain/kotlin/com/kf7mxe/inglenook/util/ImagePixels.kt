package com.kf7mxe.inglenook.util

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.ImageResource

expect suspend fun loadResizedImagePixels(
    file: FileReference,
    maxWidth: Int,
    maxHeight: Int
): ImageData

expect suspend fun loadResizedImagePixels(
    resource: ImageResource,
    maxWidth: Int,
    maxHeight: Int
): ImageData
