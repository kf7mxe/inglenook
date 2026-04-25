package com.kf7mxe.inglenook.util

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource

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

expect suspend fun loadResizedImagePixels(
    imageSource: ImageSource,
    maxWidth: Int,
    maxHeight: Int
): ImageData?

expect suspend fun loadResizedImagePixelsFromUrl(
    url: String,
    maxWidth: Int,
    maxHeight: Int
): ImageData?
