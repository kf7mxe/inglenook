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
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.util.formatFileSize
import com.kf7mxe.inglenook.storage.DangerSemantic
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.downloads.toAudioBook
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending

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

        scrolling.col {
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

                shownWhen { downloads().isEmpty() && activeDownloads().isEmpty() }.EmptyState(
                    icon = Icon.download,
                    title = "No downloaded books",
                    description = "Download books to listen offline"
                )

                shownWhen { downloads().isNotEmpty() }.card.col {
                    gap = 0.rem

                    forEach(downloads) { download ->
                        button {
                            row {
                                padding = 0.75.rem
                                gap = 1.rem

                                // Cover image (from cache) or fallback icon
                                sizedBox(SizeConstraints(width = 3.rem, height = 4.5.rem)).frame {
                                    if (download.coverImageId != null) {
                                        val cachedCover = rememberSuspending {
                                            jellyfinClient.value.fetchCoverImage(download.coverImageId, download.id)
                                        }
                                        themed(ImageSemantic).image {
                                            ::source { cachedCover() }
                                            scaleType = ImageScaleType.Crop
                                        }
                                    } else {
                                        centered.icon(Icon.book, "Book")
                                    }
                                }

                                // Book info
                                expanding.col {
                                    gap = 0.25.rem
                                    text {
                                        content = download.title
                                        ellipsis = true
                                    }
                                    subtext {
                                        content = download.authors.map{it.name}.joinToString(", ")
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
                                        DownloadManager.deleteDownload(download.id)
                                        loadDownloads()
                                    }
                                    themeChoice += DangerSemantic
                                }
                            }
                            onClick {
                                if (download.itemType == ItemType.Ebook) {
                                    // Navigate to book detail page to open reader
                                    mainPageNavigator.navigate(BookDetailPage(download.id))
                                } else {
                                    // Play the downloaded audiobook
                                    val book = download.toAudioBook()
                                    PlaybackState.play(book, 0L)
                                }
                            }
                        }
                        separator()
                    }
                }
            }
        }
    }
}

// formatFileSize moved to com.kf7mxe.inglenook.util.FormatUtils
