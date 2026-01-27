package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class DownloadsPage(val unit: Unit = Unit) : Page {
    override val title: ReactiveContext.() -> String = { "Downloads" }

    override fun ViewWriter.render() {
        val downloads = Signal<List<DownloadedBook>>(emptyList())
        val activeDownloads = Signal<Map<String, DownloadProgress>>(emptyMap())
        val totalSize = Signal(0L)
        val isLoading = Signal(true)

        // Load downloads
        AppScope.launch {
            try {
                downloads.value = DownloadManager.getDownloads()
                totalSize.value = downloads.value.sumOf { it.fileSize }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

        scrolls.col {
            padding = 1.rem
            gap = 1.rem

            // Storage usage
            card.col {
                gap = 0.5.rem
                row {
                    expanding.text("Storage Used")
                    text {
                        ::content { formatFileSize(totalSize()) }
                    }
                }
            }

            // Active downloads
            shownWhen { activeDownloads().isNotEmpty() }.col {
                gap = 0.5.rem
                h3 { content = "Downloading" }

                reactiveSuspending {
                    clearChildren()
                    for ((bookId, progress) in activeDownloads()) {
                        card.col {
                            gap = 0.25.rem
                            row {
                                expanding.text("Downloading...")
                                button {
                                    icon(Icon.close, "Cancel")
                                    onClick {
                                        AppScope.launch {
                                            DownloadManager.cancelDownload(bookId)
                                        }
                                    }
                                }
                            }
                            progressBar {
                                val ratio = if (progress.totalBytes > 0) {
                                    progress.bytesDownloaded.toFloat() / progress.totalBytes
                                } else 0f
                                this.ratio = ratio
                            }
                            subtext {
                                content = "${formatFileSize(progress.bytesDownloaded)} / ${formatFileSize(progress.totalBytes)}"
                            }
                        }
                    }
                }
            }

            // Downloaded books
            col {
                gap = 0.5.rem
                h3 { content = "Downloaded Books" }

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() && downloads().isEmpty() }.centered.col {
                    gap = 0.5.rem
                    text("No downloaded books")
                    subtext("Download books to listen offline")
                }

                shownWhen { !isLoading() && downloads().isNotEmpty() }.col {
                    reactiveSuspending {
                        clearChildren()
                        for (download in downloads()) {
                            card.row {
                                gap = 1.rem
                                padding = 0.75.rem

                                // Cover image placeholder
                                sizedBox(SizeConstraints(width = 4.rem, height = 6.rem)) {
                                    if (download.coverImagePath != null) {
                                        image {
                                            source = ImageLocal(download.coverImagePath)
                                            scaleType = ImageScaleType.Crop
                                        }
                                    } else {
                                        centered.icon(Icon.book, download.title)
                                    }
                                }

                                expanding.col {
                                    gap = 0.25.rem
                                    text(download.title)
                                    subtext(download.authors.joinToString(", ").ifEmpty { "Unknown Author" })
                                    subtext(formatFileSize(download.fileSize))
                                }

                                col {
                                    button {
                                        icon(Icon.playArrow, "Play")
                                        onClick {
                                            mainPageNavigator.navigate(BookDetailPage(download._id))
                                        }
                                    }
                                    button {
                                        icon(Icon.delete, "Delete")
                                        onClick {
                                            AppScope.launch {
                                                DownloadManager.deleteDownload(download._id)
                                                downloads.value = downloads.value.filter { it._id != download._id }
                                                totalSize.value = downloads.value.sumOf { it.fileSize }
                                            }
                                        }
                                        themeChoice = ThemeDerivation { it.copy(foreground = Color.red).withBack }.onNext
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes.toFloat() / (1024 * 1024 * 1024))
    }
}
