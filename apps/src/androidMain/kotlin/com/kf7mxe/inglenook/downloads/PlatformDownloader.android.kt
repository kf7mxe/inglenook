@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package com.kf7mxe.inglenook.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadStatus
import com.kf7mxe.inglenook.DownloadedBook
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.serverScopedProperty
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.AndroidAppContext
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

actual object PlatformDownloader {

    // Persistent mapping of downloadId (Long) to bookId (String)
    private val downloadIdToBookId: PersistentProperty<Map<Long, String>>
        get() = serverScopedProperty("downloadIdToBookId", emptyMap())

    // Persistent storage for books that are currently being downloaded
    private val pendingBooks: PersistentProperty<Map<String, Book>>
        get() = serverScopedProperty("pendingBooks", emptyMap())

    private var pollingJob: Job? = null

    init {
        // Resume polling on startup if there are active downloads
        startPollingIfNecessary()
    }

    private fun startPollingIfNecessary() {
        if (downloadIdToBookId.value.isNotEmpty() && (pollingJob == null || pollingJob?.isActive == false)) {
            pollingJob = AppScope.launch {
                while (downloadIdToBookId.value.isNotEmpty()) {
                    pollProgress()
                    delay(1000)
                }
            }
        }
    }

    private suspend fun pollProgress() {
        val context = AndroidAppContext.applicationCtx
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val ids = downloadIdToBookId.value.keys.toLongArray()
        if (ids.isEmpty()) return

        val query = DownloadManager.Query().setFilterById(*ids)
        val cursor = downloadManager.query(query)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID))
            val bookId = downloadIdToBookId.value[id] ?: continue

            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

            val downloadStatus = when (status) {
                DownloadManager.STATUS_PENDING -> DownloadStatus.Pending
                DownloadManager.STATUS_RUNNING -> DownloadStatus.Downloading
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Completed
                DownloadManager.STATUS_FAILED -> DownloadStatus.Failed
                DownloadManager.STATUS_PAUSED -> DownloadStatus.Pending
                else -> DownloadStatus.Failed
            }

            com.kf7mxe.inglenook.downloads.DownloadManager.updateProgress(DownloadProgress(
                bookId = bookId,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                status = downloadStatus
            ))
        }
        cursor.close()
    }

    /**
     * Start a download using the system DownloadManager.
     */
    actual suspend fun performDownload(
        book: Book,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedBook? {
        val client = jellyfinClient.value
            ?: throw IllegalStateException("Not connected to Jellyfin server")

        val streamUrl = client.getDownloadUrl(book)
        val authHeader = client.getAuthHeader()
        
        val context = AndroidAppContext.applicationCtx
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val ext = book.fileExtension ?: if (book.itemType == ItemType.Ebook) ".epub" else ".m4a"
        val fileName = "${book.id}$ext"
        
        val request = DownloadManager.Request(Uri.parse(streamUrl))
            .setTitle(book.title)
            .setDescription("Downloading audiobook")
            .addRequestHeader("X-Emby-Authorization", authHeader)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "audiobooks/$fileName")
        
        val downloadId = downloadManager.enqueue(request)
        
        // Store mapping
        downloadIdToBookId.value = downloadIdToBookId.value + (downloadId to book.id)
        pendingBooks.value = pendingBooks.value + (book.id to book)
        
        startPollingIfNecessary()

        // Return null — Android downloads are handled asynchronously via DownloadManager.
        return null
    }

    actual suspend fun cancelDownload(bookId: String) {
        val downloadId = downloadIdToBookId.value.filterValues { it == bookId }.keys.firstOrNull()
        if (downloadId != null) {
            val context = AndroidAppContext.applicationCtx
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            clearDownloadMapping(downloadId)
        }
    }

    actual suspend fun deleteFile(filePath: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    actual fun getDownloadsDirectory(): String {
        val context = AndroidAppContext.applicationCtx
        val downloadsDir = File(context.getExternalFilesDir(null), "audiobooks")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        return downloadsDir.absolutePath
    }

    // Helper methods for the Receiver

    fun getBookIdForDownload(downloadId: Long): String? {
        return downloadIdToBookId.value[downloadId]
    }

    fun getPendingBook(bookId: String): Book? {
        return pendingBooks.value[bookId]
    }

    fun clearDownloadMapping(downloadId: Long) {
        val bookId = downloadIdToBookId.value[downloadId]
        if (bookId != null) {
            downloadIdToBookId.value = downloadIdToBookId.value - downloadId
            pendingBooks.value = pendingBooks.value - bookId
        }
    }
}
