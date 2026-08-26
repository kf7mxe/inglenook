package com.kf7mxe.inglenook.cache

import com.lightningkite.kiteui.models.ImageSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

expect suspend fun fetchAndPersistImage(url: String, key: String): ImageSource?
expect suspend fun loadPersistedImage(key: String): ImageSource?
expect suspend fun clearPersistedImageCache()

object ImageCache {
    private const val MAX_MEMORY_ENTRIES = 100
    private val memoryCache = mutableMapOf<String, ImageSource>()
    private val accessOrder = mutableListOf<String>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ImageSource?>>()
    private val mutex = Mutex()

    private fun putInMemoryUnsafe(key: String, value: ImageSource) {
        if (memoryCache.containsKey(key)) {
            accessOrder.remove(key)
        } else if (memoryCache.size >= MAX_MEMORY_ENTRIES) {
            val oldest = accessOrder.removeAt(0)
            memoryCache.remove(oldest)
        }
        memoryCache[key] = value
        accessOrder.add(key)
    }

    private fun getFromMemoryUnsafe(key: String): ImageSource? {
        val value = memoryCache[key] ?: return null
        // Move to end (most recently used)
        accessOrder.remove(key)
        accessOrder.add(key)
        return value
    }

    fun cacheKey(url: String): String = "image_${url.hashCode().toUInt()}"

    suspend fun get(url: String): ImageSource? {
        if (url.isBlank()) return null

        val key = cacheKey(url)

        // L1: Check in-memory cache
        mutex.withLock { getFromMemoryUnsafe(key) }?.let { return it }

        // L2: Check persistent cache
        val persisted = loadPersistedImage(key)
        if (persisted != null) {

            mutex.withLock { putInMemoryUnsafe(key, persisted) }
            return persisted
        }

        // L3: Fetch from network, persist, and return. Only one request per image
        // is allowed at a time; grids commonly ask for the same image concurrently.
        val (deferred, isOwner) = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                CompletableDeferred<ImageSource?>().also { inFlight[key] = it } to true
            }
        }

        if (!isOwner) {
            return try {
                deferred.await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

        return try {
            val fetched = fetchAndPersistImage(url, key)
            mutex.withLock {
                if (fetched != null) putInMemoryUnsafe(key, fetched)
                inFlight.remove(key)
            }
            deferred.complete(fetched)
            fetched
        } catch (e: Exception) {
            mutex.withLock { inFlight.remove(key) }
            deferred.completeExceptionally(e)
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun clear() {
        mutex.withLock {
            inFlight.values.forEach { it.completeExceptionally(CancellationException("Image cache cleared")) }
            inFlight.clear()
            memoryCache.clear()
            accessOrder.clear()
        }
        clearPersistedImageCache()
    }
}
