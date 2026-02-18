@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kf7mxe.inglenook.downloads

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadStatus
import com.kf7mxe.inglenook.DownloadedBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.fetch.Response
import kotlin.js.Promise
import kotlin.time.Clock

// External declarations for IndexedDB
external class IDBFactory {
    fun open(name: String, version: Int = definedExternally): IDBOpenDBRequest
    fun deleteDatabase(name: String): IDBOpenDBRequest
}

external class IDBOpenDBRequest {
    var onupgradeneeded: ((dynamic) -> Unit)?
    var onsuccess: ((dynamic) -> Unit)?
    var onerror: ((dynamic) -> Unit)?
    val result: IDBDatabase
}

external class IDBRequest<T> {
    var onsuccess: ((dynamic) -> Unit)?
    var onerror: ((dynamic) -> Unit)?
    val result: T
}

external class IDBDatabase {
    fun transaction(storeNames: Array<String>, mode: String = definedExternally): IDBTransaction
    fun createObjectStore(name: String, options: dynamic = definedExternally): IDBObjectStore
    val objectStoreNames: dynamic
    fun close()
}

external class IDBTransaction {
    fun objectStore(name: String): IDBObjectStore
    var oncomplete: ((dynamic) -> Unit)?
    var onerror: ((dynamic) -> Unit)?
}

external class IDBObjectStore {
    fun put(value: dynamic, key: String = definedExternally): IDBRequest<dynamic>
    fun get(key: String): IDBRequest<dynamic>
    fun delete(key: String): IDBRequest<dynamic>
}

external val indexedDB: IDBFactory

actual object PlatformDownloader {
    private const val DB_NAME = "inglenook_downloads"
    private const val STORE_NAME = "audiobooks"
    private const val DB_VERSION = 1

    // Cache of blob URLs for playback
    private val blobUrls = mutableMapOf<String, String>()

    private suspend fun openDatabase(): IDBDatabase {
        return Promise<IDBDatabase> { resolve, reject ->
            val request = indexedDB.open(DB_NAME, DB_VERSION)

            request.onupgradeneeded = { event ->
                val db = event.target.result.unsafeCast<IDBDatabase>()
                // Create object store if it doesn't exist
                if (!db.objectStoreNames.contains(STORE_NAME)) {
                    db.createObjectStore(STORE_NAME)
                }
            }

            request.onsuccess = {
                resolve(request.result)
            }

            request.onerror = { event ->
                reject(Exception("Failed to open IndexedDB: ${event}"))
            }
        }.await()
    }

    actual suspend fun performDownload(
        book: Book,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedBook {
        val client = jellyfinClient.value
            ?: throw IllegalStateException("Not connected to Jellyfin server")

        val streamUrl = client.getAudioStreamUrl(book.id)

        // Report starting
        onProgress(DownloadProgress(
            bookId = book.id,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Downloading
        ))

        try {
            // Fetch the audio file with authentication
            val fetchOptions = js("{}")
            fetchOptions["headers"] = js("{}")
            fetchOptions["headers"]["X-Emby-Authorization"] = client.getAuthHeader()

            val response = kotlinx.browser.window.fetch(streamUrl, fetchOptions).await<Response>()

            if (!response.ok) {
                throw Exception("Download failed: ${response.status} ${response.statusText}")
            }

            val contentLength = response.headers.get("Content-Length")?.toLongOrNull() ?: 0L

            // Update progress to show downloading (indeterminate while fetching blob)
            onProgress(DownloadProgress(
                bookId = book.id,
                bytesDownloaded = 0L,
                totalBytes = contentLength,
                status = DownloadStatus.Downloading
            ))

            // Get the blob
            val blob = response.blob().await<org.w3c.files.Blob>()

            // Store in IndexedDB
            val db = openDatabase()
            Promise<Unit> { resolve, reject ->
                val transaction = db.transaction(arrayOf(STORE_NAME), "readwrite")
                val store = transaction.objectStore(STORE_NAME)

                // Store metadata along with blob
                val data = js("{}")
                data["blob"] = blob
                data["title"] = book.title
                data["authors"] = book.authors.toTypedArray()
                data["duration"] = book.duration
                data["chapters"] = book.chapters.map { chapter ->
                    val c = js("{}")
                    c["name"] = chapter.name
                    c["startPositionTicks"] = chapter.startPositionTicks
                    c
                }.toTypedArray()
                data["downloadedAt"] = Clock.System.now().toString()
                data["fileSize"] = blob.size.toLong()

                store.put(data, book.id)

                transaction.oncomplete = {
                    db.close()
                    resolve(Unit)
                }

                transaction.onerror = { event ->
                    db.close()
                    reject(Exception("Failed to store in IndexedDB: ${event}"))
                }
            }.await()

            // Report completed
            onProgress(DownloadProgress(
                bookId = book.id,
                bytesDownloaded = blob.size.toLong(),
                totalBytes = blob.size.toLong(),
                status = DownloadStatus.Completed
            ))

            return DownloadedBook(
                _id = book.id,
                title = book.title,
                authors = book.authors,
                localFilePath = "indexeddb://${book.id}", // Virtual path indicating IndexedDB storage
                coverImagePath = null,
                fileSize = blob.size.toLong(),
                duration = book.duration,
                chapters = book.chapters
            )
        } catch (e: Exception) {
            onProgress(DownloadProgress(
                bookId = book.id,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                status = DownloadStatus.Failed
            ))
            throw e
        }
    }

    actual suspend fun cancelDownload(bookId: String) {
        // Web fetch doesn't support cancellation easily
        // The download will complete but we won't store it
    }

    actual suspend fun deleteFile(filePath: String) {
        // Extract book ID from virtual path
        val bookId = filePath.removePrefix("indexeddb://")

        // Revoke any existing blob URL
        blobUrls[bookId]?.let { url ->
            URL.revokeObjectURL(url)
            blobUrls.remove(bookId)
        }

        // Delete from IndexedDB
        val db = openDatabase()
        Promise<Unit> { resolve, reject ->
            val transaction = db.transaction(arrayOf(STORE_NAME), "readwrite")
            val store = transaction.objectStore(STORE_NAME)
            store.delete(bookId)

            transaction.oncomplete = {
                db.close()
                resolve(Unit)
            }

            transaction.onerror = { event ->
                db.close()
                reject(Exception("Failed to delete from IndexedDB: ${event}"))
            }
        }.await()
    }

    actual fun getDownloadsDirectory(): String {
        return "indexeddb://"
    }

    /**
     * Get a blob URL for playing a downloaded audiobook.
     * The returned URL can be used as the src for an audio element.
     */
    suspend fun getBlobUrl(bookId: String): String? {
        // Check cache first
        blobUrls[bookId]?.let { return it }

        val db = openDatabase()
        val data = Promise<dynamic?> { resolve, reject ->
            val transaction = db.transaction(arrayOf(STORE_NAME), "readonly")
            val store = transaction.objectStore(STORE_NAME)
            val request = store.get(bookId)

            request.onsuccess = {
                db.close()
                resolve(request.result)
            }

            request.onerror = { event ->
                db.close()
                reject(Exception("Failed to get from IndexedDB: ${event}"))
            }
        }.await()

        if (data == null) return null

        val blob = data["blob"].unsafeCast<org.w3c.files.Blob>()
        val url = URL.createObjectURL(blob)

        // Cache the URL
        blobUrls[bookId] = url

        return url
    }
}
