package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.cache.blurServerImageAndCacheImage
import com.kf7mxe.inglenook.cache.getBlurredCachedImage
import com.kf7mxe.inglenook.components.DownloadButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.lightningkite.reactive.context.invoke
import com.kf7mxe.inglenook.components.PlaybackControls
import com.kf7mxe.inglenook.components.BookshelfPickerDialog
import com.kf7mxe.inglenook.components.blurredImage
import com.kf7mxe.inglenook.components.bookmarksList
import com.kf7mxe.inglenook.components.chaptersList
import com.kf7mxe.inglenook.ebook.ebookReader
import com.lightningkite.kiteui.views.l2.coordinatorFrame
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.util.truncateDisplay
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.views.closeThisPopover
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.context.invoke
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.card
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Routable("book/{bookId}")
class BookDetailPage(val bookId: String) : Page {
    override val title: Reactive<String> = rememberSuspending {
        book()?.title?.truncateDisplay(35) ?: "Book"
    }
    val book = rememberSuspending {
        val client = jellyfinClient()
        val book = client?.getBook(bookId)
        bookToShowBlurredBackgroundCoverOf.set(book)
        book
    }

    override fun ViewWriter.render() {
        val showChapters = Signal(true)
        padded.col {
            // Loading state
            shownWhen { !book.state().ready }.expanding.centered.col {
                activityIndicator { }
            }

            // Connection error state (book failed to load due to network)
            shownWhen { book.state().ready && book() == null && ConnectivityState.lastNetworkError() != null }.expanding.connectionError {
                mainPageNavigator.navigate(BookDetailPage(bookId))
            }

            // Scrollable content
            expanding.scrolling.col {

                shownWhen { book() != null && book.state().ready }.padded.col {
                    row {
                        val cachedCover = rememberSuspending {
                            val currentBook = book()
                            val client = jellyfinClient()
                            if (client != null && currentBook?.coverImageId != null) {
                                ImageCache.get(client.getImageUrl(currentBook.coverImageId, currentBook.id))
                            } else null
                        }

                        sizeConstraints(width = 12.rem).frame {
                            shownWhen { book()?.coverImageId != null }.themed(ImageSemantic).image {
                                ::source { cachedCover() }
                                scaleType = ImageScaleType.Fit
                            }
                            shownWhen { book()?.coverImageId == null }.centered.icon {
                                source = Icon.book.copy(width = 4.rem, height = 4.rem)
                                description = "Book cover"
                            }
                        }

                        expanding.col {

                            h2 { ::content { book()?.title ?: "" } }

                            val authorInfos = remember { book()?.authorInfos ?: emptyList() }

                            text {
                                ::shown {
                                    authorInfos().isEmpty()
                                }
                                content = "Unknown Author"
                            }

                            col {
                                forEach(authorInfos) { author ->
                                    button {
                                        text {
                                            ::content {
                                                author.name
                                            }
                                        }
                                        onClick {
                                            author.id?.let { id ->
                                                mainPageNavigator.navigate(AuthorDetailPage(id))
                                            }
                                        }
                                    }

                                }
                            }

                            // Narrator (reactive)
                            shownWhen { book()?.narrator != null }.row {
                                subtext { content = "Narrated by " }
                                subtext { ::content { book()?.narrator ?: "" } }
                            }

                            // Series info
                            shownWhen { book()?.seriesName != null }.subtext {
                                ::content {
                                    val b = book()
                                    if (b?.indexNumber != null) {
                                        "${b.seriesName} #${b.indexNumber}"
                                    } else {
                                        b?.seriesName ?: ""
                                    }
                                }
                            }

                            // Duration
                            subtext {
                                ::shown{
                                    book()?.itemType == ItemType.AudioBook
                                }
                                ::content {
                                    val durationTicks = book()?.duration ?: 0L
                                    val totalSeconds = durationTicks / 10_000_000
                                    val hours = totalSeconds / 3600
                                    val minutes = (totalSeconds % 3600) / 60
                                    if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                                }
                            }

                            // Progress indicator
                            shownWhen { (book()?.userData?.playbackPositionTicks ?: 0L) > 0L }.col {
                                progressBar {
                                    ::ratio {
                                        val b = book()
                                        val position = b?.userData?.playbackPositionTicks ?: 0L
                                        val dur = b?.duration ?: 1L
                                        if (dur > 0) (position.toFloat() / dur).coerceAtMost(1f) else 0f
                                    }
                                }
                                subtext {
                                    ::content {
                                        val b = book()
                                        val position = b?.userData?.playbackPositionTicks ?: 0L
                                        val dur = b?.duration ?: 1L
                                        val percent = if (dur > 0) ((position.toFloat() / dur) * 100).toInt()
                                            .coerceAtMost(100) else 0
                                        "$percent% complete"
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons for audiobooks
                    shownWhen { book()?.itemType == ItemType.AudioBook }.row {
                        expanding.button {
                            row {
                                centered.icon {
                                    ::source {
                                        if (PlaybackState.currentBook()?.id == book()?.id && PlaybackState.isPlaying()) Icon.pause else
                                            Icon.playArrow
                                    }
                                }
                                centered.text {
                                    ::content {
                                        val position = book()?.userData?.playbackPositionTicks ?: 0L
                                        if (position > 0) "Continue" else "Play"
                                    }
                                }
                            }
                            onClick {
                                val currentBook = book()
                                if (currentBook != null) {
                                    if (PlaybackState.currentBook.value?.id == currentBook.id && PlaybackState.isPlaying.value) {
                                        PlaybackState.pause()
                                    } else {
                                        val startPosition = currentBook.userData?.playbackPositionTicks ?: 0L
                                        PlaybackState.play(currentBook, startPosition)
                                    }
                                }
                            }
                            themeChoice += ImportantSemantic
                        }

                        // Download button - inline implementation for reactive access
                        button {
                            centered.row {
                                gap = 0.5.rem

                                icon {
                                    ::source {
                                        val currentBook = book()
                                        if (currentBook == null) {
                                            Icon.download
                                        } else {
                                            val activeDownloads =
                                                com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads()
                                            val activeProgress = activeDownloads[currentBook.id]

                                            when {
                                                com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(
                                                    currentBook.id
                                                ) -> Icon.checkCircle

                                                activeProgress != null -> when (activeProgress.status) {
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

                                text {
                                    ::content {
                                        val currentBook = book()
                                        if (currentBook == null) {
                                            "Download"
                                        } else {
                                            val activeDownloads =
                                                com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads()
                                            val activeProgress = activeDownloads[currentBook.id]

                                            when {
                                                com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(
                                                    currentBook.id
                                                ) -> "Downloaded"

                                                activeProgress != null -> when (activeProgress.status) {
                                                    DownloadStatus.Downloading -> {
                                                        if (activeProgress.totalBytes > 0) {
                                                            val percent =
                                                                (activeProgress.bytesDownloaded * 100 / activeProgress.totalBytes).toInt()
                                                            "Downloading $percent%"
                                                        } else {
                                                            "Downloading..."
                                                        }
                                                    }

                                                    DownloadStatus.Pending -> "Waiting..."
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
                                val currentBook = book() ?: return@Action
                                val isDownloaded =
                                    com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(currentBook.id)
                                val hasActiveDownload =
                                    com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads.value.containsKey(
                                        currentBook.id
                                    )

                                when {
                                    isDownloaded -> com.kf7mxe.inglenook.downloads.DownloadManager.deleteDownload(
                                        currentBook.id
                                    )

                                    hasActiveDownload -> com.kf7mxe.inglenook.downloads.DownloadManager.cancelDownload(
                                        currentBook.id
                                    )

                                    else -> com.kf7mxe.inglenook.downloads.DownloadManager.downloadBook(currentBook)
                                }
                            }
                        }

                        bookshelfButton(bookId)
                    }

                    // Action buttons for ebooks
                    shownWhen { book()?.itemType == ItemType.Ebook }.col {
                        // Read button - opens in-app reader
                        row {
                            expanding.button {
                                row {
                                    centered.icon(Icon.book, "Read")
                                    centered.text { content = "Read" }
                                }
                                onClick {
                                    if (com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Web) mainPageNavigator.navigate(
                                        EbookReaderPage(bookId)
                                    )
                                    if (com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Android) {
                                        val client = jellyfinClient.value
                                        if (client != null) {
                                            val downloadUrl = "${client.serverUrl}/Items/$bookId/Download"
                                            val authHeader = client.getAuthHeader()
                                            ebookReader(bookId, downloadUrl, authHeader)
                                        }
                                    }
                                }
                                themeChoice += ImportantSemantic
                            }
                            // Download button for ebook
                            button {
                                centered.row {
                                    gap = 0.5.rem
                                    icon {
                                        ::source {
                                            val currentBook = book()
                                            if (currentBook == null) Icon.download else {
                                                val activeProgress =
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads()[currentBook.id]
                                                when {
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(currentBook.id) -> Icon.checkCircle
                                                    activeProgress?.status == DownloadStatus.Downloading -> Icon.cloudDownload
                                                    activeProgress?.status == DownloadStatus.Failed -> Icon.errorIcon
                                                    else -> Icon.download
                                                }
                                            }
                                        }
                                        description = "Download status"
                                    }
                                    text {
                                        ::content {
                                            val currentBook = book()
                                            if (currentBook == null) "Download" else {
                                                val activeProgress =
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads()[currentBook.id]
                                                when {
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(currentBook.id) -> "Downloaded"
                                                    activeProgress?.status == DownloadStatus.Downloading -> {
                                                        if (activeProgress.totalBytes > 0) {
                                                            val percent =
                                                                (activeProgress.bytesDownloaded * 100 / activeProgress.totalBytes).toInt()
                                                            "Downloading $percent%"
                                                        } else "Downloading..."
                                                    }

                                                    activeProgress?.status == DownloadStatus.Failed -> "Failed - Retry"
                                                    else -> "Download"
                                                }
                                            }
                                        }
                                    }
                                }
                                action = Action("Download Ebook") {
                                    val currentBook = book() ?: return@Action
                                    val isDownloaded =
                                        com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(currentBook.id)
                                    val activeProgress =
                                        com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads.value[currentBook.id]
                                    when {
                                        isDownloaded -> com.kf7mxe.inglenook.downloads.DownloadManager.deleteDownload(
                                            currentBook.id
                                        )

                                        activeProgress?.status == DownloadStatus.Downloading -> com.kf7mxe.inglenook.downloads.DownloadManager.cancelDownload(
                                            currentBook.id
                                        )

                                        activeProgress?.status == DownloadStatus.Failed -> com.kf7mxe.inglenook.downloads.DownloadManager.downloadBook(
                                            currentBook
                                        )

                                        activeProgress == null -> com.kf7mxe.inglenook.downloads.DownloadManager.downloadBook(
                                            currentBook
                                        )
                                    }
                                }
                            }

                            bookshelfButton(bookId)
                        }



                        // Open in browser
                        button {
                            row {
                                centered.icon(Icon.chevronRight, "Open in Browser")
                                centered.text { content = "Open in Browser" }
                            }
                            onClick {
                                val currentBook = book()
                                val client = jellyfinClient.value
                                if (currentBook != null && client != null) {
                                    // Open the book reader URL in browser
                                    val readerUrl =
                                        "${client.serverUrl}/web/index.html#!/details?id=${currentBook.id}"
                                    // Use platform-specific URL opener
                                    com.kf7mxe.inglenook.util.openUrl(readerUrl)
                                }
                            }
                        }
                    }

                    // Bookshelf membership
                    val memberBookshelves = remember {
                        BookshelfRepository.getBookshelvesContainingBook(bookId)
                    }
                    shownWhen { memberBookshelves().isNotEmpty() }.col {
                        h3 { content = "Bookshelves" }
                        scrollingHorizontally.row {
                            forEach(memberBookshelves) { shelf ->
                                card.button {
                                    row {
                                        icon(Icon.collectionsBookmark.copy(width = 1.rem, height = 1.rem), "Bookshelf")
                                        text { content = shelf.name }
                                    }
                                    onClick {
                                        mainPageNavigator.navigate(BookshelfDetailPage(shelf._id.toString()))
                                    }
                                }
                            }
                        }
                    }

                    // Description section
                    shownWhen { book()?.description != null }.col {
                        h3 { content = "Description" }
                        text { ::setBasicHtmlContent { book()?.description ?: "" } }
                    }

                    // Chapters section (only for audiobooks)
                    shownWhen {
                        val b = book()
                        b?.itemType == ItemType.AudioBook && (b.chapters.size) > 0
                    }.col {
                        unpadded. button {
                            paddingByEdge = Edges(0.rem,0.rem,1.rem,1.rem)
                            row {
                                expanding.h3 {
                                    ::content { "Chapters (${book()?.chapters?.size ?: 0})" }
                                }
                                icon {
                                    ::source { if (showChapters()) Icon.unfoldLess else Icon.unfoldMore }
                                    description = "Toggle chapters"
                                }
                            }
                            onClick { showChapters.value = !showChapters.value }
                        }

                        shownWhen { showChapters() }.unpadded.col {
                            // Render chapters with reactive updates using forEachUpdating
                            val chapters = remember {
                                book()?.chapters ?: emptyList()
                            }

                            chaptersList(chapters) { chapter ->
                                val currentBook = book()
                                if (currentBook != null) {
                                    PlaybackState.play(currentBook, chapter.startPositionTicks)
                                }
                            }
                        }
                    }

                    // Bookmarks section
                    val showBookmarks = Signal(false)
                    val bookmarks = Signal(BookmarkRepository.getBookmarksForBook(bookId))

                    shownWhen { bookmarks().isNotEmpty() }.col {
                        unpadded.button {
                            row {
                                expanding.h3 {
                                    ::content { "Bookmarks (${bookmarks().size})" }
                                }
                                icon {
                                    ::source { if (showBookmarks()) Icon.unfoldLess else Icon.unfoldMore }
                                    description = "Toggle bookmarks"
                                }
                            }
                            onClick { showBookmarks.value = !showBookmarks.value }
                        }

                        shownWhen { showBookmarks() }.col {
                            bookmarksList(bookId, bookmarks) { positionTicks ->
                                book()?.let { currentBook ->
                                    PlaybackState.play(currentBook, positionTicks)
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

private fun ViewWriter.bookshelfButton(bookId: String) {
    button {
        icon(Icon.collectionsBookmark, "Add to Bookshelf")
        onClick {
            coordinatorFrame?.bottomSheet(
                partialRatio = 0.75f,
                startState = BottomSheetState.PARTIALLY_EXPANDED
            ) { control ->
                unpadded.BookshelfPickerDialog(bookId) {
                    control.close()
                }
            }
        }
    }
}
