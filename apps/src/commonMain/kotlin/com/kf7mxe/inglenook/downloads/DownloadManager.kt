package com.kf7mxe.inglenook.downloads

import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.serverScopedProperty
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.reactive.core.Signal

// Download manager for offline playback
object DownloadManager {
    // Stored downloads (persisted, scoped per server)
    private val storedDownloads: PersistentProperty<List<DownloadedBook>>
        get() = serverScopedProperty("downloads", emptyList())

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

    suspend fun downloadBook(book: Book) {
        // Add to active downloads — set Downloading immediately so UI shows progress
        activeDownloads.value = activeDownloads.value + (book.id to DownloadProgress(
            bookId = book.id,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Downloading
        ))

        try {
            // Pre-cache cover image so it's available offline
            val coverUrl = book.coverImageId?.let { coverId ->
                jellyfinClient.value?.getImageUrl(coverId, book.id)
            }
            if (coverUrl != null) {
                try { ImageCache.get(coverUrl) } catch (_: Exception) { }
            }

            // Start download using platform-specific implementation.
            // Returns null if the download is handled asynchronously (e.g. Android service).
            val downloadedBook = PlatformDownloader.performDownload(book) { progress ->
                activeDownloads.value = activeDownloads.value + (book.id to progress)
            }

            if (downloadedBook != null) {
                // Synchronous completion (JS/iOS) — add to stored downloads
                storedDownloads.value = storedDownloads.value + downloadedBook
                activeDownloads.value = activeDownloads.value - book.id
            }
            // null means async (Android service) — progress via DownloadManager callbacks
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

    // Methods called by platform-specific download implementations

    /**
     * Update the progress of an active download.
     */
    fun updateProgress(progress: DownloadProgress) {
        activeDownloads.value = activeDownloads.value + (progress.bookId to progress)
    }

    /**
     * Called when a download completes successfully.
     */
    fun notifyDownloadComplete(downloadedBook: DownloadedBook) {
        // Add to stored downloads
        storedDownloads.value = storedDownloads.value + downloadedBook

        // Update active downloads to completed status
        activeDownloads.value = activeDownloads.value + (downloadedBook._id to DownloadProgress(
            bookId = downloadedBook._id,
            bytesDownloaded = downloadedBook.fileSize,
            totalBytes = downloadedBook.fileSize,
            status = DownloadStatus.Completed
        ))

        // Remove from active downloads
        activeDownloads.value = activeDownloads.value - downloadedBook._id
    }

    /**
     * Called when a download fails.
     */
    fun notifyDownloadFailed(bookId: String, error: String) {
        activeDownloads.value = activeDownloads.value + (bookId to DownloadProgress(
            bookId = bookId,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Failed
        ))
    }

    /**
     * Called when a download is cancelled.
     */
    fun notifyDownloadCancelled(bookId: String) {
        activeDownloads.value = activeDownloads.value + (bookId to DownloadProgress(
            bookId = bookId,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Cancelled
        ))

        // Remove from active downloads
        activeDownloads.value = activeDownloads.value - bookId
    }
}

fun DownloadedBook.toAudioBook(): Book = Book(
    id = _id,
    title = title,
    authors = authors,
    coverImageId = coverImageId,
    duration = duration,
    chapters = chapters,
    itemType = itemType,
)
