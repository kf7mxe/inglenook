package com.kf7mxe.inglenook.cache

import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CacheRefresher {
    private var shortTtlJob: Job? = null
    private var defaultTtlJob: Job? = null

    private const val SHORT_INTERVAL_MS = 45_000L   // 45s (under 1-min SHORT_TTL)
    private const val DEFAULT_INTERVAL_MS = 240_000L // 4 min (under 5-min DEFAULT_TTL)

    fun start() {
        stop()

        // Refresh SHORT_TTL data (in-progress, recently added) every 45s
        shortTtlJob = AppScope.launch {
            while (true) {
                delay(SHORT_INTERVAL_MS)
                if (ConnectivityState.offlineMode.value) continue
                val client = jellyfinClient.value ?: continue
                try { client.getInProgressBooks() } catch (_: Exception) {}
                try { client.getRecentlyAddedBooks() } catch (_: Exception) {}
            }
        }

        // Refresh DEFAULT_TTL data (suggested, all books, authors) every 4 min
        defaultTtlJob = AppScope.launch {
            while (true) {
                delay(DEFAULT_INTERVAL_MS)
                if (ConnectivityState.offlineMode.value) continue
                val client = jellyfinClient.value ?: continue
                try { client.getSuggestedBooks() } catch (_: Exception) {}
                try { client.getAllBooks() } catch (_: Exception) {}
                try { client.getAuthors() } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        shortTtlJob?.cancel()
        shortTtlJob = null
        defaultTtlJob?.cancel()
        defaultTtlJob = null
    }
}
