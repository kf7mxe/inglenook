@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kf7mxe.inglenook.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadStatus
import com.kf7mxe.inglenook.DownloadedBook
import com.kf7mxe.inglenook.MainActivity
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 2001

        // Action constants
        const val ACTION_START_DOWNLOAD = "com.kf7mxe.inglenook.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.kf7mxe.inglenook.CANCEL_DOWNLOAD"
        const val EXTRA_BOOK_ID = "book_id"

        private var instance: DownloadService? = null
        fun getInstance(): DownloadService? = instance
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadQueue = ConcurrentLinkedQueue<Book>()
    private val activeDownloads = ConcurrentHashMap<String, Boolean>()
    private var currentDownloadJob: Job? = null
    private var currentBookId: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Start foreground IMMEDIATELY to avoid ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, createNotification("Preparing download...", 0))

        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                // Book data is passed via DownloadManager, we just start processing queue
                processQueue()
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
                if (bookId != null) {
                    cancelDownload(bookId)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audiobook download progress"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress >= 0) {
            builder.setProgress(100, progress, progress == 0)
            builder.setContentText("$progress%")
        }

        return builder.build()
    }

    private fun updateNotification(title: String, progress: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress))
    }

    fun queueDownload(book: Book) {
        downloadQueue.add(book)
        processQueue()
    }

    private fun processQueue() {
        if (currentDownloadJob != null && currentDownloadJob?.isActive == true) {
            return // Already downloading
        }

        val book = downloadQueue.poll() ?: run {
            // Queue is empty, stop service
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        currentBookId = book.id
        activeDownloads[book.id] = true

        currentDownloadJob = serviceScope.launch {
            try {
                performDownload(book)
            } catch (e: Exception) {
                // Download failed
                DownloadManager.notifyDownloadFailed(book.id, e.message ?: "Unknown error")
            } finally {
                activeDownloads.remove(book.id)
                currentBookId = null
                // Process next in queue
                processQueue()
            }
        }
    }

    private suspend fun performDownload(book: Book) {
        val client = jellyfinClient.value
            ?: throw IllegalStateException("Not connected to Jellyfin server")

        val streamUrl = client.getAudioStreamUrl(book.id)
        val downloadsDir = PlatformDownloader.getDownloadsDirectory()
        val fileName = "${book.id}.m4a"
        val outputFile = File(downloadsDir, fileName)

        // Create downloads directory if it doesn't exist
        File(downloadsDir).mkdirs()

        // Update progress to starting
        DownloadManager.updateProgress(DownloadProgress(
            bookId = book.id,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            status = DownloadStatus.Downloading
        ))
        updateNotification("Downloading: ${book.title}", 0)

        withContext(Dispatchers.IO) {
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection

            // Add authentication header
            client.getAuthHeader().let { authHeader ->
                connection.setRequestProperty("X-Emby-Authorization", authHeader)
            }

            connection.connect()

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            try {
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastProgressUpdate = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Check if download was cancelled
                            if (activeDownloads[book.id] != true) {
                                throw InterruptedException("Download cancelled")
                            }

                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Update progress (throttle to every 1%)
                            val progress = if (totalBytes > 0) {
                                (bytesDownloaded * 100 / totalBytes).toInt()
                            } else 0

                            if (progress > lastProgressUpdate) {
                                lastProgressUpdate = progress
                                DownloadManager.updateProgress(DownloadProgress(
                                    bookId = book.id,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    status = DownloadStatus.Downloading
                                ))
                                updateNotification("Downloading: ${book.title}", progress)
                            }
                        }
                    }
                }

                connection.disconnect()

                // Download complete
                val downloadedBook = DownloadedBook(
                    _id = book.id,
                    title = book.title,
                    authors = book.authors,
                    localFilePath = outputFile.absolutePath,
                    coverImagePath = null,
                    fileSize = bytesDownloaded,
                    duration = book.duration,
                    chapters = book.chapters
                )

                DownloadManager.notifyDownloadComplete(downloadedBook)
                updateNotification("Download complete: ${book.title}", 100)

            } catch (e: InterruptedException) {
                // Cancelled - clean up
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                DownloadManager.notifyDownloadCancelled(book.id)
                throw e
            } catch (e: Exception) {
                // Failed - clean up
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                throw e
            }
        }
    }

    fun cancelDownload(bookId: String) {
        activeDownloads[bookId] = false

        // Remove from queue if queued
        downloadQueue.removeIf { it.id == bookId }
    }
}
