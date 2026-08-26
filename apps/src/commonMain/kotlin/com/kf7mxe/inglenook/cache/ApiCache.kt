@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kf7mxe.inglenook.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Simple in-memory cache for API responses with TTL support.
 *
 * Non-suspend methods (get, put, invalidate, clear, getStale) assume main-thread access.
 * The suspend getOrPut() overloads are protected by a Mutex for safe concurrent use.
 */
object ApiCache {

    private val mutex = Mutex()

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
    private val inFlight = mutableMapOf<String, CompletableDeferred<Any>>()

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
        inFlight.values.forEach { it.completeExceptionally(CancellationException("Cache cleared")) }
        inFlight.clear()
        cache.clear()
    }

    /**
     * Get or compute a value, using the cache if available.
     * [onError] is called when compute fails, even if stale data is returned.
     */
    suspend fun <T : Any> getOrPut(
        key: String,
        ttl: Duration = DEFAULT_TTL,
        onError: ((Exception) -> Unit)? = null,
        compute: suspend () -> T
    ): T = getOrPut(key, ttl, false, onError, compute)

    /**
     * Gets or computes a value while coalescing concurrent requests for the same key.
     * Force refresh bypasses the cache, but still joins an already-running request.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getOrPut(
        key: String,
        ttl: Duration = DEFAULT_TTL,
        forceRefresh: Boolean = false,
        onError: ((Exception) -> Unit)? = null,
        compute: suspend () -> T
    ): T {
        if (!forceRefresh) {
            mutex.withLock { get<T>(key) }?.let { return it }
        }

        val (deferred, isOwner) = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                CompletableDeferred<Any>().also { inFlight[key] = it } to true
            }
        }

        if (!isOwner) {
            return try {
                deferred.await() as T
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError?.invoke(e)
                val stale = mutex.withLock { getStale<T>(key) }
                if (stale != null) stale else throw e
            }
        }

        return try {
            val computed = compute()
            mutex.withLock {
                put(key, computed, ttl)
                inFlight.remove(key)
            }
            deferred.complete(computed)
            computed
        } catch (e: Exception) {
            mutex.withLock { inFlight.remove(key) }
            deferred.completeExceptionally(e)
            if (e is CancellationException) throw e
            onError?.invoke(e)
            val stale = mutex.withLock { getStale<T>(key) }
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
