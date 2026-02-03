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
import com.kf7mxe.inglenook.ebook.ebookReader
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.util.openUrl
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.launch

/**
 * Ebook reader page that downloads and renders ebooks locally.
 * Supports ePub and PDF formats using epub.js.
 */
@Routable("reader/{bookId}")
class EbookReaderPage(val bookId: String) : Page {
    override val title: Reactive<String> = Constant("Reading")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val bookInfo = Signal<AudioBook?>(null)
        val errorMessage = Signal<String?>(null)
        val showReader = Signal(false)

        // Load book details
        fun loadBook() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        bookInfo.value = client.getBook(bookId)
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
            gap = 0.rem

            // Loading state
            shownWhen { isLoading() }.expanding.centered.activityIndicator()

            // Error state
            shownWhen { errorMessage() != null && !isLoading() }.expanding.centered.col {
                gap = 0.5.rem
                text { ::content { errorMessage() ?: "" } }
                button {
                    text("Retry")
                    onClick { loadBook() }
                }
            }

            // Book info screen (before starting to read)
            shownWhen { bookInfo() != null && !isLoading() && !showReader() }.expanding.scrolling.col {
                gap = 1.5.rem
                padding = 1.rem

                // Book cover and title
                centered.col {
                    gap = 1.rem

                    // Cover image
                    sizedBox(SizeConstraints(width = 10.rem, height = 15.rem)).image {
                        ::source {
                            val currentBook = bookInfo()
                            val client = jellyfinClient()
                            if (client != null && currentBook?.coverImageId != null) {
                                ImageRemote(client.getImageUrl(currentBook.coverImageId, currentBook.id))
                            } else null
                        }
                        scaleType = ImageScaleType.Crop
                    }

                    // Title
                    centered.h2 { ::content { bookInfo()?.title ?: "" } }

                    // Author
                    centered.subtext {
                        ::content {
                            val authors = bookInfo()?.authors ?: emptyList()
                            if (authors.isNotEmpty()) "by ${authors.joinToString(", ")}" else ""
                        }
                    }
                }

                // Reading options
                centered.col {
                    gap = 0.75.rem

                    // Start reading button
                    button {
                        row {
                            gap = 0.5.rem
                            centered.icon {
                                source = Icon.book
                                description = "Read"
                            }
                            centered.text { content = "Start Reading" }
                        }
                        onClick {
                            showReader.value = true
                        }
                        themeChoice += ImportantSemantic
                    }

                    // Open in external browser as fallback
                    button {
                        row {
                            gap = 0.5.rem
                            centered.icon {
                                source = Icon.chevronRight
                                description = "Open externally"
                            }
                            centered.text { content = "Open in Browser" }
                        }
                        onClick {
                            val client = jellyfinClient.value
                            if (client != null) {
                                val readerUrl = "${client.serverUrl}/web/index.html#!/details?id=$bookId"
                                openUrl(readerUrl)
                            }
                        }
                    }
                }

                // Description
                shownWhen { bookInfo()?.description != null }.col {
                    gap = 0.5.rem
                    h3 { content = "Description" }
                    text { ::content { bookInfo()?.description ?: "" } }
                }
            }

            // Embedded ebook reader
            shownWhen { showReader() && bookInfo() != null }.expanding.col {
                gap = 0.rem

                // Close button bar
                row {
                    padding = 0.5.rem
                    gap = 0.5.rem

                    button {
                        row {
                            gap = 0.25.rem
                            icon {
                                source = Icon.arrowBack
                                description = "Back"
                            }
                            text("Close")
                        }
                        onClick {
                            showReader.value = false
                        }
                    }

                    expanding.centered.text {
                        ::content { bookInfo()?.title ?: "Reading" }
                        ellipsis = true
                    }
                }

                separator()

                // The actual ebook reader
                expanding.frame {
                    val client = jellyfinClient.value
                    if (client != null) {
                        val downloadUrl = "${client.serverUrl}/Items/$bookId/Download"
                        val authHeader = client.getAuthHeader()
                        ebookReader(downloadUrl, authHeader)
                    }
                }
            }
        }
    }
}
