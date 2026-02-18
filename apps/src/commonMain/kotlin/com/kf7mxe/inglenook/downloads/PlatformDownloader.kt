package com.kf7mxe.inglenook.downloads

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadedBook

/**
 * Platform-specific download implementation
 */
expect object PlatformDownloader {
    /**
     * Download an audiobook file to local storage
     * @param book The audiobook to download
     * @param onProgress Callback for download progress updates
     * @return The downloaded book information, or null if the download is handled asynchronously (e.g. Android service)
     */
    suspend fun performDownload(
        book: Book,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedBook?

    /**
     * Cancel an in-progress download
     * @param bookId The ID of the book being downloaded
     */
    suspend fun cancelDownload(bookId: String)

    /**
     * Delete a downloaded file
     * @param filePath The path to the file to delete
     */
    suspend fun deleteFile(filePath: String)

    /**
     * Get the downloads directory path
     */
    fun getDownloadsDirectory(): String
}
