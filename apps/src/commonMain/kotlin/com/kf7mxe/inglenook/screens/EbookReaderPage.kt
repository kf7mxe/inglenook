package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.FullScreen
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.ebook.ebookReader
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.rememberSuspending

/**
 * Ebook reader page that downloads and renders ebooks using Readium toolkit.
 * Android: Launches a dedicated ReaderActivity with Readium's EpubNavigatorFragment.
 * Web/JS: Renders directly into the DOM using epub.js with full reader controls.
 */
@Routable("reader/{bookId}")
class EbookReaderPage(val bookId: String) : Page, FullScreen {
    override val title: Reactive<String> = Constant("Reading")

    override fun ViewWriter.render() {
        val bookInfo = rememberSuspending {
            val client = jellyfinClient.value
            client?.getBook(bookId)
        }

        col {
            gap = 0.rem

            // Loading state
//            shownWhen { !bookInfo.state().ready }.expanding.centered.activityIndicator()

            // Embedded ebook reader
                gap = 0.rem
                // The actual ebook reader
                expanding.frame {
                    val client = jellyfinClient.value
                    if (client != null) {
                        val downloadUrl = "${client.serverUrl}/Items/$bookId/Download"
                        val authHeader = client.getAuthHeader()
                        ebookReader(bookId, downloadUrl, authHeader)
                    }
                }
            }
    }
}
