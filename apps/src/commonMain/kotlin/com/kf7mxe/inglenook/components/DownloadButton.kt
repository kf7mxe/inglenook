package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.playback.PlaybackState.currentBook
import com.lightningkite.kiteui.lottie.models.LottieRaw
import com.lightningkite.kiteui.lottie.views.direct.LottieView
import com.lightningkite.kiteui.lottie.views.direct.lottie
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.remember

/**
 * A button that shows download status and allows downloading books for offline playback.
 * Reactive overload - accepts a Reactive<Book?> for use in pages where book data is loaded reactively.
 */
fun ViewWriter.DownloadButton(bookReactive: Reactive<Book?>) {
    unpadded.button {
        var downloadAnimations: LottieView? = null
        unpadded.centered.row {
            gap = 0.5.rem

            val downloadStatus = remember {
                val activeDownloads = DownloadManager.activeDownloads()
                val activeProgress =bookReactive()?.let { book ->
                     activeDownloads[book.id]
                }
//                println("DEBUG downloadStatus ${downlaodStatus}")
                activeProgress
            }

            icon {
                ::shown{
                     downloadStatus()?.status != DownloadStatus.Downloading
                }
                ::source {
                    val currentBook = bookReactive()
                    if (currentBook == null) {
                        Icon.download
                    } else {
                        when {
                            DownloadManager.isDownloaded(currentBook.id) -> Icon.checkCircle
                            downloadStatus() != null -> when (downloadStatus()?.status) {
                                DownloadStatus.Downloading -> Icon.cloudDownload
                                DownloadStatus.Pending -> Icon.schedule
                                DownloadStatus.Failed -> Icon.errorIcon
                                else -> Icon.download
                            }
                            else -> Icon.download
                        }
                    }
                }
                description = "Download status"
            }
            shownWhen {
                val test = downloadStatus()?.status == DownloadStatus.Downloading
                println("DEBUG is active progress: $test")
                test
            }.unpadded.centered.sizeConstraints(width = 2.5.rem, height = 2.5.rem).lottie(
                source = LottieRaw(LottieAnimations.downloading),
                description = "Downloading "
            ) {
                downloadAnimations = this
                loop = true
                autoPlay = false
                colorTransform = { lottieColor ->
                    if(lottieColor.layerName == "bg") appTheme.value.background.closestColor()
                    else lottieColor.color
//                    Color.red

                }
                }

            centered.text {
                ::content {
                    val currentBook = bookReactive()
                    if (currentBook == null) {
                        "Download"
                    } else {
                        val activeProgress = DownloadManager.activeDownloads()[currentBook.id]
                        when {
                            DownloadManager.isDownloaded(currentBook.id) -> "Downloaded"
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
        }

        action = Action("Download") {
            val currentBook = bookReactive.invoke() ?: return@Action
            val isDownloaded = DownloadManager.isDownloaded(currentBook.id)
            val activeProgress = DownloadManager.activeDownloads.value[currentBook.id]

            when {
                isDownloaded -> DownloadManager.deleteDownload(currentBook.id)
                activeProgress?.status == DownloadStatus.Downloading -> DownloadManager.cancelDownload(currentBook.id)
                activeProgress?.status == DownloadStatus.Failed -> {
                    downloadAnimations?.pause()
                    DownloadManager.downloadBook(currentBook)
                }
                activeProgress == null -> {
                    downloadAnimations?.play()
                    DownloadManager.downloadBook(currentBook)
                }
            }
        }
    }
}

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
