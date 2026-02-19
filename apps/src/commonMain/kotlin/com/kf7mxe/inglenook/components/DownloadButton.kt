package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.lightningkite.kiteui.reactive.Action

/**
 * A button that shows download status and allows downloading books for offline playback.
 */
fun ViewWriter.DownloadButton(book: Book) {
    button {
        centered.row {
            gap = 0.5.rem

            // Show icon based on download status
            icon {
                ::source {
                    val activeDownloads = DownloadManager.activeDownloads()
                    val activeProgress = activeDownloads[book.id]

                    when {
                        DownloadManager.isDownloaded(book.id) -> Icon.checkCircle
                        activeProgress != null -> when (activeProgress.status) {
                            DownloadStatus.Downloading -> Icon.cloudDownload
                            DownloadStatus.Pending -> Icon.schedule
                            DownloadStatus.Failed -> Icon.errorIcon
                            else -> Icon.download
                        }
                        else -> Icon.download
                    }
                }
                description = "Download status"
            }

            text {
                ::content {
                    val activeDownloads = DownloadManager.activeDownloads()
                    val activeProgress = activeDownloads[book.id]

                    when {
                        DownloadManager.isDownloaded(book.id) -> "Downloaded"
                        activeProgress != null -> when (activeProgress.status) {
                            DownloadStatus.Downloading -> {
                                if (activeProgress.totalBytes > 0) {
                                    val percent = (activeProgress.bytesDownloaded * 100 / activeProgress.totalBytes).toInt()
                                    "Downloading $percent%"
                                } else {
                                    "Downloading..."
                                }
                            }
                            DownloadStatus.Pending -> "Starting download..."
                            DownloadStatus.Failed -> "Failed - Retry"
                            DownloadStatus.Cancelled -> "Cancelled"
                            else -> "Download"
                        }
                        else -> "Download"
                    }
                }
            }
        }

        action = Action("Download") {
            val isDownloaded = DownloadManager.isDownloaded(book.id)
            val activeProgress = DownloadManager.activeDownloads.value[book.id]

            when {
                isDownloaded -> DownloadManager.deleteDownload(book.id)
                activeProgress?.status == DownloadStatus.Downloading -> DownloadManager.cancelDownload(book.id)
                activeProgress?.status == DownloadStatus.Failed -> DownloadManager.downloadBook(book)
                activeProgress == null -> DownloadManager.downloadBook(book)
            }
        }

        // Style based on status
        themeChoice += ThemeDerivation { theme ->
            val isDownloaded = DownloadManager.isDownloaded(book.id)
            if (isDownloaded) {
                theme.copy(id = "downloaded", iconOverride = Color.green.darken(0.2f)).withoutBack
            } else {
                theme.withoutBack
            }
        }
    }
}
