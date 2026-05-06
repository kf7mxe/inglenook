package com.kf7mxe.inglenook.storage

import com.kf7mxe.inglenook.Bookshelf
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.BookshelfResponse
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.jellyfin.serverScopedProperty
import com.lightningkite.kiteui.reactive.PersistentProperty
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Repository for managing bookshelves — server-first with local cache fallback
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
object BookshelfRepository {
    // Local cache (persisted, scoped per server)
    private val storedBookshelves: PersistentProperty<List<Bookshelf>>
        get() = serverScopedProperty("bookshelves", emptyList())

    private val migrated: PersistentProperty<Boolean>
        get() = serverScopedProperty("bookshelvesMigrated", false)

    // Session cache for endpoint availability to avoid redundant checks
    private var endpointAvailableCache: Boolean? = null
    private var lastCheckedServer: String? = null

    /** Checks whether the Inglenook bookshelf endpoint is reachable by trying to query bookshelves. */
    suspend fun bookshelfEndpointAvailable(): Boolean {
        println("DEBUG bookshelfEndpointAvailable isOnline ${isOnline()}")
        if (!isOnline()) return false

        println("DEBUG bookshelfEndpointAvailable jellyfinServerConfig.value?.bookshelvesAvailable ${jellyfinServerConfig.value?.bookshelvesAvailable}")
        // If the server config already knows bookshelves are available, trust it.
        if (jellyfinServerConfig.value?.bookshelvesAvailable == true) return true

        println("DEBUG bookshelfEndpointAvailable jellyfinClient.value ${jellyfinClient.value}")

        val client = jellyfinClient.value ?: return false


        val currentServer = client.serverUrl
        println("DEBUG bookshelfEndpointAvailable lastCheckedServer == currentServer && endpointAvailableCache == true ${lastCheckedServer == currentServer && endpointAvailableCache == true}")
        // If we previously confirmed the endpoint is available in this session, use that.
        if (lastCheckedServer == currentServer && endpointAvailableCache == true) {
            return true
        }

        val available = client.bookshelfEndpointAvailable()
        
        // Cache the result. If available is false, we'll still store it to avoid hammering the network 
        // within the same view lifecycle, but since it's not 'true', it may be re-checked later.
        endpointAvailableCache = available
        lastCheckedServer = currentServer
        return available
    }

    private fun isOnline(): Boolean =
        !ConnectivityState.offlineMode.value && jellyfinClient.value != null

    private fun BookshelfResponse.toBookshelf(): Bookshelf = Bookshelf(
        _id = try { Uuid.parse(Id) } catch (_: Exception) { Uuid.random() },
        name = Name,
        bookIds = BookIds,
        coverImageUrl = CoverImageUrl,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private suspend fun migrateLocalBookshelvesIfNeeded() {
        if (migrated.value) return
        val client = jellyfinClient.value ?: return
        val localShelves = storedBookshelves.value
        if (localShelves.isEmpty()) {
            migrated.value = true
            return
        }

        // Check if the endpoint is available before attempting migration
        if (!bookshelfEndpointAvailable()) return

        var anyFailed = false
        // Upload each local bookshelf to server
        for (shelf in localShelves) {
            try {
                val created = client.createBookshelf(shelf.name)
                if (created != null) {
                    if (shelf.bookIds.isNotEmpty()) {
                        client.updateBookshelf(created.Id, name = null, bookIds = shelf.bookIds)
                    }
                } else {
                    anyFailed = true
                }
            } catch (_: Exception) {
                anyFailed = true
            }
        }
        
        // Only mark as migrated if we didn't experience any failures
        if (!anyFailed) {
            migrated.value = true
        }
    }

    suspend fun getAllBookshelves(): List<Bookshelf> {
        if (isOnline()) {
            try {
                val client = jellyfinClient.value!!
                
                // Only attempt server sync if the endpoint is available
                if (bookshelfEndpointAvailable()) {
                    migrateLocalBookshelvesIfNeeded()
                    val serverShelves = client.getBookshelves().map { it.toBookshelf() }
                    
                    // Cache locally - only overwrite if we got a successful response
                    storedBookshelves.value = serverShelves
                    return serverShelves.sortedBy { it.name.lowercase() }
                }
            } catch (e: Exception) {
                println("DEBUG getAllBookshelves: server sync failed, falling back to local. Error: $e")
                // Fall through to local cache
            }
        }
        return storedBookshelves.value.sortedBy { it.name.lowercase() }
    }

    suspend fun getBookshelf(id: Uuid): Bookshelf? {
        return getAllBookshelves().find { it._id == id }
    }

    suspend fun createBookshelf(name: String): Bookshelf {
        if (isOnline()) {
            try {
                val client = jellyfinClient.value!!
                val response = client.createBookshelf(name)
                if (response != null) {
                    val bookshelf = response.toBookshelf()
                    storedBookshelves.value = storedBookshelves.value + bookshelf
                    return bookshelf
                }
            } catch (_: Exception) {
                // Fall through to local creation
            }
        }
        // Offline/fallback: create locally
        val bookshelf = Bookshelf(
            _id = Uuid.random(),
            name = name,
            bookIds = emptyList(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        storedBookshelves.value = storedBookshelves.value + bookshelf
        return bookshelf
    }

    suspend fun updateBookshelf(bookshelf: Bookshelf) {
        val updated = bookshelf.copy(updatedAt = Clock.System.now())
        if (isOnline()) {
            try {
                val client = jellyfinClient.value!!
                client.updateBookshelf(
                    id = bookshelf._id.toString(),
                    name = bookshelf.name,
                    bookIds = bookshelf.bookIds,
                    coverImageUrl = bookshelf.coverImageUrl
                )
            } catch (_: Exception) {
                // Update locally anyway
            }
        }
        storedBookshelves.value = storedBookshelves.value.map {
            if (it._id == bookshelf._id) updated else it
        }
    }

    suspend fun deleteBookshelf(id: Uuid) {
        if (isOnline()) {
            try {
                val client = jellyfinClient.value!!
                client.deleteBookshelf(id.toString())
            } catch (_: Exception) {
                // Delete locally anyway
            }
        }
        storedBookshelves.value = storedBookshelves.value.filter { it._id != id }
    }

    suspend fun addBookToBookshelf(bookshelfId: Uuid, bookId: String) {
        val bookshelf = getBookshelf(bookshelfId) ?: return
        if (bookId !in bookshelf.bookIds) {
            val updated = bookshelf.copy(
                bookIds = bookshelf.bookIds + bookId,
                updatedAt = Clock.System.now()
            )
            if (isOnline()) {
                try {
                    val client = jellyfinClient.value!!
                    client.updateBookshelf(
                        id = bookshelfId.toString(),
                        name = null,
                        bookIds = updated.bookIds
                    )
                } catch (_: Exception) {
                    // Update locally anyway
                }
            }
            storedBookshelves.value = storedBookshelves.value.map {
                if (it._id == bookshelfId) updated else it
            }
        }
    }

    suspend fun removeBookFromBookshelf(bookshelfId: Uuid, bookId: String) {
        val bookshelf = getBookshelf(bookshelfId) ?: return
        val updated = bookshelf.copy(
            bookIds = bookshelf.bookIds - bookId,
            updatedAt = Clock.System.now()
        )
        if (isOnline()) {
            try {
                val client = jellyfinClient.value!!
                client.updateBookshelf(
                    id = bookshelfId.toString(),
                    name = null,
                    bookIds = updated.bookIds
                )
            } catch (_: Exception) {
                // Update locally anyway
            }
        }
        storedBookshelves.value = storedBookshelves.value.map {
            if (it._id == bookshelfId) updated else it
        }
    }

    suspend fun getBookshelvesContainingBook(bookId: String): List<Bookshelf> {
        return getAllBookshelves().filter { bookId in it.bookIds }
    }
}
