package com.kf7mxe.inglenook.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kf7mxe.inglenook.DownloadedBook
import com.lightningkite.kiteui.views.AndroidAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DownloadCompletedReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId != -1L) {
                scope.launch {
                    handleDownloadComplete(context, downloadId)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun handleDownloadComplete(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = cursor.getString(localUriIndex)
                
                val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val title = cursor.getString(titleIndex)

                // The bookId was stored in the description or we can extract it from the file path
                // For simplicity, let's assume we can find it. 
                // We'll update PlatformDownloader to store the mapping.
                
                val bookId = PlatformDownloader.getBookIdForDownload(downloadId)
                if (bookId != null) {
                    val book = PlatformDownloader.getPendingBook(bookId)
                    if (book != null) {
                        val file = File(Uri.parse(localUri).path!!)
                        val downloadedBook = DownloadedBook(
                            id = book.id,
                            title = book.title,
                            authors = book.authors,
                            localFilePath = file.absolutePath,
                            coverImagePath = null,
                            coverImageId = book.coverImageId,
                            fileSize = file.length(),
                            duration = book.duration,
                            chapters = book.chapters,
                            itemType = book.itemType
                        )
                        com.kf7mxe.inglenook.downloads.DownloadManager.notifyDownloadComplete(downloadedBook)
                    }
                    PlatformDownloader.clearDownloadMapping(downloadId)
                }
            } else if (status == DownloadManager.STATUS_FAILED) {
                val bookId = PlatformDownloader.getBookIdForDownload(downloadId)
                if (bookId != null) {
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIndex)
                    com.kf7mxe.inglenook.downloads.DownloadManager.notifyDownloadFailed(bookId, "Download failed with reason: $reason")
                    PlatformDownloader.clearDownloadMapping(downloadId)
                }
            }
        }
        cursor.close()
    }
}
