@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kf7mxe.inglenook.downloads

import android.content.Intent
import android.os.Build
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadedBook
import com.lightningkite.kiteui.views.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object PlatformDownloader {

    /**
     * Start a download using the foreground DownloadService.
     * Progress and completion are reported through DownloadManager callbacks.
     */
    actual suspend fun performDownload(
        book: Book,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedBook {
        // Queue the download with the service
        val service = DownloadService.getInstance()
        if (service != null) {
            // Service is already running - queue directly
            service.queueDownload(book)
        } else {
            // Start the service
            val context = AndroidAppContext.applicationCtx
            val intent = Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START_DOWNLOAD
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            // Queue download once service starts (it will pick up pending items)
            // The service will be started and will call processQueue()
            // We need to queue the book - do this via a small delay or callback
            // For now, we'll re-queue once the service is available
            kotlinx.coroutines.delay(100) // Small delay to let service start
            DownloadService.getInstance()?.queueDownload(book)
        }

        // Return a placeholder - actual completion is handled via DownloadManager callbacks
        // The service will call DownloadManager.notifyDownloadComplete() when done
        throw UnsupportedOperationException(
            "Android downloads are handled asynchronously via DownloadService. " +
            "Use DownloadManager callbacks for progress and completion."
        )
    }

    actual suspend fun cancelDownload(bookId: String) {
        // Cancel via service
        DownloadService.getInstance()?.cancelDownload(bookId)

        // Also try sending intent if service isn't available
        val context = AndroidAppContext.applicationCtx
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_BOOK_ID, bookId)
        }
        context.startService(intent)
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
