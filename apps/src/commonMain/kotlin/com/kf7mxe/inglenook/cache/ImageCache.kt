package com.kf7mxe.inglenook.cache

import com.lightningkite.kiteui.models.ImageSource

expect suspend fun fetchAndPersistImage(url: String, key: String): ImageSource?
expect suspend fun loadPersistedImage(key: String): ImageSource?
expect suspend fun clearPersistedImageCache()

object ImageCache {
    private const val MAX_MEMORY_ENTRIES = 100
    private val memoryCache = mutableMapOf<String, ImageSource>()
    private val accessOrder = mutableListOf<String>()

    private fun putInMemory(key: String, value: ImageSource) {
        if (memoryCache.containsKey(key)) {
            accessOrder.remove(key)
        } else if (memoryCache.size >= MAX_MEMORY_ENTRIES) {
            val oldest = accessOrder.removeFirst()
            memoryCache.remove(oldest)
        }
        memoryCache[key] = value
        accessOrder.add(key)
    }

    private fun getFromMemory(key: String): ImageSource? {
        val value = memoryCache[key] ?: return null
        // Move to end (most recently used)
        accessOrder.remove(key)
        accessOrder.add(key)
        return value
    }

    fun cacheKey(url: String): String {
        val afterItems = url.substringAfter("/Items/", "")
        return if (afterItems.isNotEmpty()) {
            afterItems.replace("/", "-")
        } else {
            url.hashCode().toUInt().toString()
        }
    }

    suspend fun get(url: String): ImageSource? {
        if (url.isBlank()) return null

        val key = cacheKey(url)

        // L1: Check in-memory cache
        getFromMemory(key)?.let { return it }

        // L2: Check persistent cache
        val persisted = loadPersistedImage(key)
        if (persisted != null) {
            putInMemory(key, persisted)
            return persisted
        }

        // L3: Fetch from network, persist, and return
        val fetched = fetchAndPersistImage(url, key)
        if (fetched != null) {
            putInMemory(key, fetched)
        }
        return fetched
    }

    suspend fun clear() {
        memoryCache.clear()
        accessOrder.clear()
        clearPersistedImageCache()
    }
}
