package com.kf7mxe.inglenook.downloads

import com.kf7mxe.inglenook.*
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.reactive.core.Signal

// Download manager for offline playback
object DownloadManager {
    // Stored downloads (persisted)
    private val storedDownloads = PersistentProperty<List<DownloadedBook>>("downloads", emptyList())

    // Active downloads (in progress)
    val activeDownloads = Signal<Map<String, DownloadProgress>>(emptyMap())

    fun getDownloads(): List<DownloadedBook> {
        return storedDownloads.value
    }

    fun getDownload(bookId: String): DownloadedBook? {
        return storedDownloads.value.find { it._id == bookId }
    }

    fun isDownloaded(bookId: String): Boolean {
        return storedDownloads.value.any { it._id == bookId }
    }

    fun getLocalFilePath(bookId: String): String? {
        return storedDownloads.value.find { it._id == bookId }?.localFilePath
    }

    suspend fun downloadBook(book: AudioBook) {
        // Add to active downloads
        activeDownloads.value = activeDownloads.value + (book.id to DownloadProgress(
            bookId = book.id,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Pending
        ))

        try {
            // Start download using platform-specific implementation
            val downloadedBook = PlatformDownloader.performDownload(book) { progress ->
                activeDownloads.value = activeDownloads.value + (book.id to progress)
            }

            // Add to stored downloads
            storedDownloads.value = storedDownloads.value + downloadedBook

            // Remove from active downloads
            activeDownloads.value = activeDownloads.value - book.id
        } catch (e: Exception) {
            // Mark as failed
            activeDownloads.value = activeDownloads.value + (book.id to DownloadProgress(
                bookId = book.id,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                status = DownloadStatus.Failed
            ))
        }
    }

    suspend fun cancelDownload(bookId: String) {
        // Cancel any active download
        PlatformDownloader.cancelDownload(bookId)

        activeDownloads.value = activeDownloads.value + (bookId to DownloadProgress(
            bookId = bookId,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Cancelled
        ))

        // Remove from active downloads after a short delay
        activeDownloads.value = activeDownloads.value - bookId
    }

    suspend fun deleteDownload(bookId: String) {
        val download = storedDownloads.value.find { it._id == bookId }
        if (download != null) {
            // Delete the file using platform-specific implementation
            PlatformDownloader.deleteFile(download.localFilePath)

            // Remove from stored downloads
            storedDownloads.value = storedDownloads.value.filter { it._id != bookId }
        }
    }
}
