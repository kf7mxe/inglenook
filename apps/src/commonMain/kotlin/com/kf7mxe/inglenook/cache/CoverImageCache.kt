package com.kf7mxe.inglenook.cache

import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.lightningkite.kiteui.models.ImageSource

/**
 * Fetches and caches a cover image from the Jellyfin server.
 * Must be called inside a rememberSuspending { } block.
 *
 * @param imageId The image tag ID (coverImageId for books, imageId for authors/series)
 * @param itemId Optional item ID for the URL path. If null, imageId is used as the item ID.
 */
suspend fun JellyfinClient?.fetchCoverImage(imageId: String?, itemId: String? = null): ImageSource? {
    println("DEBUG fetchCoverImage ${imageId}")
    println("DEBUG fetchCoverImage ${itemId}")
    if (this == null || imageId == null) return null
    return ImageCache.get(getImageUrl(imageId, itemId))
}
