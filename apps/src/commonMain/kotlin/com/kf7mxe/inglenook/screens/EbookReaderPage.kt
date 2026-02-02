package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.download
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.util.openUrl
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.launch

/**
 * Ebook reader page that displays ebooks.
 * Currently opens ebooks in the external browser/reader,
 * but provides a hub for book information and reading options.
 */
@Routable("reader/{bookId}")
class EbookReaderPage(val bookId: String) : Page {
    override val title: Reactive<String> = Constant("Reading")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val book = Signal<AudioBook?>(null)
        val errorMessage = Signal<String?>(null)

        // Load book details
        fun loadBook() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        book.value = client.getBook(bookId)
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load book: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadBook()

        col {
            gap = 1.rem
            padding = 1.rem

            // Loading state
            shownWhen { isLoading() }.centered.activityIndicator()

            // Error state
            shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                gap = 0.5.rem
                text { ::content { errorMessage() ?: "" } }
                button {
                    text("Retry")
                    onClick { loadBook() }
                }
            }

            // Book info and reading options
            shownWhen { book() != null && !isLoading() }.expanding.col {
                gap = 1.5.rem

                // Book cover and title
                centered.col {
                    gap = 1.rem

                    // Cover image
                    sizedBox(SizeConstraints(width = 10.rem, height = 15.rem)).image {
                        ::source {
                            val currentBook = book()
                            val client = jellyfinClient()
                            if (client != null && currentBook?.coverImageId != null) {
                                ImageRemote(client.getImageUrl(currentBook.coverImageId, currentBook.id))
                            } else null
                        }
                        scaleType = ImageScaleType.Crop
                    }

                    // Title
                    centered.h2 { ::content { book()?.title ?: "" } }

                    // Author
                    centered.subtext {
                        ::content {
                            val authors = book()?.authors ?: emptyList()
                            if (authors.isNotEmpty()) "by ${authors.joinToString(", ")}" else ""
                        }
                    }
                }

                // Reading options
                centered.col {
                    gap = 0.75.rem

                    // Open in Jellyfin Web Reader
                    button {
                        row {
                            gap = 0.5.rem
                            centered.icon {
                                source = Icon.book
                                description = "Read in Browser"
                            }
                            centered.text { content = "Read in Browser" }
                        }
                        onClick {
                            val client = jellyfinClient.value
                            if (client != null) {
                                // Jellyfin's web reader
                                val readerUrl = "${client.serverUrl}/web/index.html#!/details?id=$bookId"
                                openUrl(readerUrl)
                            }
                        }
                        themeChoice += ImportantSemantic
                    }

                    // Download the file
                    button {
                        row {
                            gap = 0.5.rem
                            centered.icon {
                                source = Icon.download
                                description = "Download"
                            }
                            centered.text { content = "Download File" }
                        }
                        onClick {
                            val client = jellyfinClient.value
                            if (client != null) {
                                // Direct download URL
                                val downloadUrl = "${client.serverUrl}/Items/$bookId/Download"
                                openUrl(downloadUrl)
                            }
                        }
                    }
                }

                // Description
                shownWhen { book()?.description != null }.col {
                    gap = 0.5.rem
                    h3 { content = "Description" }
                    text { ::content { book()?.description ?: "" } }
                }
            }
        }
    }
}
