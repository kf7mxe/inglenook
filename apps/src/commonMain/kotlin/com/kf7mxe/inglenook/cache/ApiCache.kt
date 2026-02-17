@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kf7mxe.inglenook.cache

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Simple in-memory cache for API responses with TTL support.
 */
object ApiCache {

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMs: Long
    ) {
        fun isExpired(): Boolean {
            return Clock.System.now().toEpochMilliseconds() - timestamp > ttlMs
        }
    }

    private val cache = mutableMapOf<String, CacheEntry<Any>>()

    // Default TTL values
    val DEFAULT_TTL: Duration = 5.minutes
    val SHORT_TTL: Duration = 1.minutes
    val LONG_TTL: Duration = 15.minutes

    /**
     * Get a cached value if it exists and hasn't expired.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = cache[key] ?: return null
        if (entry.isExpired()) {
            cache.remove(key)
            return null
        }
        return entry.data as? T
    }

    /**
     * Store a value in the cache with the specified TTL.
     */
    fun <T : Any> put(key: String, data: T, ttl: Duration = DEFAULT_TTL) {
        cache[key] = CacheEntry(
            data = data,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            ttlMs = ttl.inWholeMilliseconds
        )
    }

    /**
     * Invalidate cache entries matching a pattern.
     * Pattern supports simple prefix matching with '*'.
     */
    fun invalidate(pattern: String) {
        if (pattern.endsWith("*")) {
            val prefix = pattern.dropLast(1)
            cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
        } else {
            cache.remove(pattern)
        }
    }

    /**
     * Invalidate all cache entries.
     */
    fun clear() {
        cache.clear()
    }

    /**
     * Get or compute a value, using the cache if available.
     */
    suspend fun <T : Any> getOrPut(
        key: String,
        ttl: Duration = DEFAULT_TTL,
        compute: suspend () -> T
    ): T {
        val cached = get<T>(key)
        if (cached != null) {
            return cached
        }

        return try {
            val computed = compute()
            put(key, computed, ttl)
            computed
        } catch (e: Exception) {
            val stale = getStale<T>(key)
            if (stale != null) stale else throw e
        }
    }

    /**
     * Get or compute a value, using the cache if available.
     * Force refresh will bypass the cache and recompute.
     * On failure, returns stale cached data if available rather than propagating the error.
     */
    suspend fun <T : Any> getOrPut(
        key: String,
        ttl: Duration = DEFAULT_TTL,
        forceRefresh: Boolean = false,
        compute: suspend () -> T
    ): T {
        if (!forceRefresh) {
            val cached = get<T>(key)
            if (cached != null) {
                return cached
            }
        }

        return try {
            val computed = compute()
            put(key, computed, ttl)
            computed
        } catch (e: Exception) {
            // On failure, return stale cache (even if expired) rather than crashing
            val stale = getStale<T>(key)
            if (stale != null) stale else throw e
        }
    }

    /**
     * Get a cached value even if expired (for fallback on errors).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getStale(key: String): T? {
        val entry = cache[key] ?: return null
        return entry.data as? T
    }

    // Convenience methods for common cache keys

    fun booksKey(libraryIds: List<String>): String =
        "books_${libraryIds.sorted().joinToString(",")}"

    fun authorsKey(libraryIds: List<String>): String =
        "authors_${libraryIds.sorted().joinToString(",")}"

    fun seriesKey(libraryIds: List<String>): String =
        "series_${libraryIds.sorted().joinToString(",")}"

    fun bookKey(bookId: String): String = "book_$bookId"

    fun authorKey(authorId: String): String = "author_$authorId"

    fun inProgressKey(libraryIds: List<String>): String =
        "inprogress_${libraryIds.sorted().joinToString(",")}"

    fun recentKey(libraryIds: List<String>): String =
        "recent_${libraryIds.sorted().joinToString(",")}"

    fun suggestedKey(libraryIds: List<String>): String =
        "suggested_${libraryIds.sorted().joinToString(",")}"
}
