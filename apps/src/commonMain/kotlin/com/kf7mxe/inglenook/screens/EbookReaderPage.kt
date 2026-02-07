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
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch

/**
 * Ebook reader page that downloads and renders ebooks locally.
 * Supports ePub and PDF formats using epub.js.
 */
@Routable("reader/{bookId}")
class EbookReaderPage(val bookId: String) : Page {
    override val title: Reactive<String> = Constant("Reading")

    override fun ViewWriter.render() {
//        val isLoading = Signal(true)
        val bookInfo = rememberSuspending {
            val client = jellyfinClient.value
            client?.getBook(bookId)
        }
        val errorMessage = Signal<String?>(null)
//        val showReader = Signal(false)



        col {
            gap = 0.rem

            // Loading state
            shownWhen { !bookInfo.state().ready}.expanding.centered.activityIndicator()

//            // Error state
//            shownWhen { errorMessage() != null && bookInfo.state().ready }.expanding.centered.col {
//                gap = 0.5.rem
//                text { ::content { errorMessage() ?: "" } }
//                button {
//                    text("Retry")
//                    onClick { loadBook() }
//                }
//            }

            // Embedded ebook reader
            shownWhen { bookInfo.state().ready && bookInfo() != null }.expanding.col {
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
//                            showReader.value = false
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
