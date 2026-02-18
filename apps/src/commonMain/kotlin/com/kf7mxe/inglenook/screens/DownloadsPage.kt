package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.remember

@Routable("/downloads")
class DownloadsPage : Page {
    override val title get() = Constant("Downloads")

    override fun ViewWriter.render() {
        val downloads = Signal<List<DownloadedBook>>(emptyList())
        val activeDownloads = remember {
            DownloadManager.activeDownloads().toList()
        }

        // Load downloads
        fun loadDownloads() {
            downloads.value = DownloadManager.getDownloads()
        }

        // Initial load
        loadDownloads()

        scrolls.col {
            padding = 1.rem
            gap = 1.5.rem

            // Storage info card
            card.col {
                gap = 0.5.rem
                row {
                    expanding.text("Storage Used")
                    text {
                        ::content {
                            val totalBytes = downloads().sumOf { it.fileSize }
                            formatFileSize(totalBytes)
                        }
                    }
                }

                row {
                    expanding.text("Downloaded Books")
                    text { ::content { downloads().size.toString() } }
                }
            }

            // Active downloads section
            shownWhen { activeDownloads().isNotEmpty() }.col {
                gap = 0.5.rem
                h3 { content = "Downloading" }

                card.col {
                    gap = 0.rem

                    forEach(activeDownloads ) { progress ->
                        row {
                            padding = 0.75.rem
                            gap = 0.75.rem

                            expanding.col {
                                gap = 0.25.rem
                                text(progress.second.bookId)
                                progressBar {
                                    ratio = if (progress.second.totalBytes > 0) {
                                        progress.second.bytesDownloaded.toFloat() / progress.second.totalBytes
                                    } else 0f
                                }
                                subtext {
                                    content = when (progress.second.status) {
                                        DownloadStatus.Pending -> "Waiting..."
                                        DownloadStatus.Downloading -> {
                                            "${formatFileSize(progress.second.bytesDownloaded)} / ${formatFileSize(progress.second.totalBytes)}"
                                        }
                                        DownloadStatus.Failed -> "Failed"
                                        DownloadStatus.Cancelled -> "Cancelled"
                                        else -> ""
                                    }
                                }
                            }

                            button {
                                icon(Icon.close, "Cancel")
                                action = Action("Cancel download") {
                                    DownloadManager.cancelDownload(progress.second.bookId)
                                }
                            }
                        }
                        separator()
                    }
                }
            }

            // Downloaded books section
            col {
                gap = 0.5.rem
                h3 { content = "Downloaded Books" }

                shownWhen { downloads().isEmpty() && activeDownloads().isEmpty() }.centered.col {
                    padding = 2.rem
                    gap = 0.5.rem
                    icon(Icon.download.copy(width = 3.rem, height = 3.rem), "Downloads")
                    text("No downloaded books")
                    subtext("Download books to listen offline")
                }

                shownWhen { downloads().isNotEmpty() }.card.col {
                    gap = 0.rem

                    forEach(downloads) { download ->
                        button {
                            row {
                                padding = 0.75.rem
                                gap = 1.rem

                                // Thumbnail placeholder
                                sizedBox(SizeConstraints(width = 3.rem, height = 4.5.rem)).frame {
                                    centered.icon(Icon.book, "Book")
                                }

                                // Book info
                                expanding.col {
                                    gap = 0.25.rem
                                    text {
                                        content = download.title
                                        ellipsis = true
                                    }
                                    subtext {
                                        content = download.authors.joinToString(", ")
                                        ellipsis = true
                                    }
                                    subtext {
                                        content = formatFileSize(download.fileSize)
                                    }
                                }

                                // Delete button
                                button {
                                    icon(Icon.delete, "Delete")
                                    action = Action("Delete download") {
                                        DownloadManager.deleteDownload(download._id)
                                        loadDownloads()
                                    }
                                    themeChoice += ThemeDerivation { it.copy(id = "danger", foreground = Color.red).withoutBack }
                                }
                            }
                            onClick {
                                // Play the downloaded book
                                val book = Book(
                                    id = download._id,
                                    title = download.title,
                                    authors = download.authors,
                                    duration = download.duration,
                                    chapters = download.chapters
                                )
                                PlaybackState.play(book, 0L)
                            }
                        }
                        separator()
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
        else -> {
            val gb = bytes.toDouble() / (1024 * 1024 * 1024)
            "${(gb * 100).toLong() / 100.0} GB"
        }
    }
}
