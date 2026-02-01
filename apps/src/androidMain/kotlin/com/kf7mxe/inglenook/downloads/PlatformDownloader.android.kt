@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kf7mxe.inglenook.downloads

import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadStatus
import com.kf7mxe.inglenook.DownloadedBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.views.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

actual object PlatformDownloader {
    // Track active download jobs for cancellation
    private val activeDownloads = ConcurrentHashMap<String, Boolean>()

    actual suspend fun performDownload(
        book: AudioBook,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedBook = withContext(Dispatchers.IO) {
        val client = jellyfinClient.value
            ?: throw IllegalStateException("Not connected to Jellyfin server")

        val streamUrl = client.getAudioStreamUrl(book.id)
        val downloadsDir = getDownloadsDirectory()
        val fileName = "${book.id}.m4a"
        val outputFile = File(downloadsDir, fileName)

        // Mark this download as active
        activeDownloads[book.id] = true

        try {
            // Create downloads directory if it doesn't exist
            File(downloadsDir).mkdirs()

            // Open connection
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection

            // Add authentication header if needed
            client.getAuthHeader()?.let { authHeader ->
                connection.setRequestProperty("X-Emby-Authorization", authHeader)
            }

            connection.connect()

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            // Update progress to downloading
            onProgress(DownloadProgress(
                bookId = book.id,
                bytesDownloaded = 0L,
                totalBytes = totalBytes,
                status = DownloadStatus.Downloading
            ))

            // Download the file
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check if download was cancelled
                        if (activeDownloads[book.id] != true) {
                            throw InterruptedException("Download cancelled")
                        }

                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // Update progress
                        onProgress(DownloadProgress(
                            bookId = book.id,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            status = DownloadStatus.Downloading
                        ))
                    }
                }
            }

            connection.disconnect()

            // Update progress to completed
            onProgress(DownloadProgress(
                bookId = book.id,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                status = DownloadStatus.Completed
            ))

            // Return downloaded book info
            DownloadedBook(
                _id = book.id,
                title = book.title,
                authors = book.authors,
                localFilePath = outputFile.absolutePath,
                coverImagePath = null, // Cover image not downloaded separately
                // downloadedAt uses default value (Clock.System.now())
                fileSize = bytesDownloaded,
                duration = book.duration,
                chapters = book.chapters
            )
        } catch (e: Exception) {
            // Clean up partial download
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        } finally {
            activeDownloads.remove(book.id)
        }
    }

    actual suspend fun cancelDownload(bookId: String) {
        // Mark download as cancelled
        activeDownloads[bookId] = false
    }

    actual suspend fun deleteFile(filePath: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    actual fun getDownloadsDirectory(): String {
        val context = AndroidAppContext.applicationCtx
        val downloadsDir = File(context.filesDir, "audiobooks")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        return downloadsDir.absolutePath
    }
}
