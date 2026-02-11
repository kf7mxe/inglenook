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
import com.kf7mxe.inglenook.cache.blurServerImageAndCacheImage
import com.kf7mxe.inglenook.cache.getBlurredCachedImage
import com.kf7mxe.inglenook.components.DownloadButton
import com.lightningkite.reactive.context.invoke
import com.kf7mxe.inglenook.components.PlaybackControls
import com.kf7mxe.inglenook.components.BookshelfPickerDialog
import com.kf7mxe.inglenook.components.blurredImage
import com.kf7mxe.inglenook.ebook.ebookReader
import com.lightningkite.kiteui.views.l2.coordinatorFrame
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.views.closeThisPopover
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import io.ktor.util.Platform
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Routable("book/{bookId}")
class BookDetailPage(val bookId: String) : Page {
    override val title: Reactive<String> = rememberSuspending {
     book()?.title?:"Book"
    }
    val book = rememberSuspending {
        val client = jellyfinClient()
       client?.getBook(bookId)

    }
    override fun ViewWriter.render() {


//        val errorMessage = Signal<String?>(null)
        val showChapters = Signal(true)




        unpadded.frame {
            // Blurred background layer (only when enabled in theme settings)

            blurredImage(book,remember {
                persistedThemeSettings().showPlayingBookCoverOnNowPlayingAndBookDetail
            })

            // Content layer
//            ThemeDerivation.invoke { it.withBack }.onNext.
            col {
                gap = 0.rem

                // Scrollable content
                expanding.scrolling.col {
                    padding = 1.rem
                    gap = 1.5.rem

                    // Loading state
//                shownWhen { isLoading() }.centered.activityIndicator()

                    // Error state
//                shownWhen { errorMessage() != null && !book.state().ready }.centered.col {
//                    gap = 0.5.rem
//                    text { ::content { errorMessage() ?: "" } }
//                    button {
//                        text("Retry")
////                        onClick { loadBook() }
//                    }
//                }

                    // Book content
                    shownWhen { book() != null && book.state().ready }. col {
                        gap = 1.5.rem

                        // Book header with cover and info
                        row {
                            gap = 1.rem

                            // Cover image
                            sizeConstraints(width = 12.rem).frame {
                                shownWhen { book()?.coverImageId != null }.themed(ImageSemantic).image {
                                    ::source {
                                        val currentBook = book()
                                        val client = jellyfinClient()
                                        if (client != null && currentBook?.coverImageId != null) {
                                            ImageRemote(client.getImageUrl(currentBook.coverImageId, currentBook.id))
                                        } else null
                                    }
                                    scaleType = ImageScaleType.Fit
                                }
                                shownWhen { book()?.coverImageId == null }.centered.icon {
                                    source = Icon.book.copy(width = 4.rem, height = 4.rem)
                                    description = "Book cover"
                                }
                            }

                            // Book info
                            expanding.col {
                                gap = 0.25.rem

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
                                            // Author names (reactive)
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
                                    gap = 0.rem
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
                                    gap = 0.25.rem
                                    progressBar {
                                        ::ratio {
                                            val b = book()
                                            val position = b?.userData?.playbackPositionTicks ?: 0L
                                            val dur = b?.duration ?: 1L
                                            if (dur > 0) (position.toFloat() / dur) else 0f
                                        }
                                    }
                                    subtext {
                                        ::content {
                                            val b = book()
                                            val position = b?.userData?.playbackPositionTicks ?: 0L
                                            val dur = b?.duration ?: 1L
                                            val percent = if (dur > 0) ((position.toFloat() / dur) * 100).toInt() else 0
                                            "$percent% complete"
                                        }
                                    }
                                }
                            }
                        }

                        // Action buttons for audiobooks
                        shownWhen { book()?.itemType == ItemType.AudioBook }.row {
                            gap = 0.5.rem

                            expanding.button {
                                row {
                                    gap = 0.5.rem
                                    centered.icon(Icon.playArrow, "Play")
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
                                        val startPosition = currentBook.userData?.playbackPositionTicks ?: 0L
                                        PlaybackState.play(currentBook, startPosition)
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

                                onClick {
                                    val currentBook = book()
                                    if (currentBook != null) {
                                        val isDownloaded =
                                            com.kf7mxe.inglenook.downloads.DownloadManager.isDownloaded(currentBook.id)
                                        val hasActiveDownload =
                                            com.kf7mxe.inglenook.downloads.DownloadManager.activeDownloads.value.containsKey(
                                                currentBook.id
                                            )

                                        when {
                                            isDownloaded -> {
                                                AppScope.launch {
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.deleteDownload(
                                                        currentBook.id
                                                    )
                                                }
                                            }

                                            hasActiveDownload -> {
                                                AppScope.launch {
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.cancelDownload(
                                                        currentBook.id
                                                    )
                                                }
                                            }

                                            else -> {
                                                AppScope.launch {
                                                    com.kf7mxe.inglenook.downloads.DownloadManager.downloadBook(
                                                        currentBook
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Bookshelf button
                            button {
                                icon(Icon.collectionsBookmark, "Add to Bookshelf")
                                onClick {
                                    coordinatorFrame?.openBottomSheet(
                                        halfScreenRatio = 0.7f,
                                        dim = true
                                    ) {
                                        BookshelfPickerDialog(bookId) {
                                            // Close bottom sheet on dismiss
//                                        close()
                                            closeThisPopover()
                                        }
                                    }
                                }
                            }
                        }

                        // Action buttons for ebooks
                        shownWhen { book()?.itemType == ItemType.Ebook }.col {
                            gap = 0.5.rem

                            // Read button - opens in-app reader
                            row {
                                gap = 0.5.rem

                                expanding.button {
                                    row {
                                        gap = 0.5.rem
                                        centered.icon(Icon.book, "Read")
                                        centered.text { content = "Read" }
                                    }
                                    onClick {
                                        if(com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Web) mainPageNavigator.navigate(EbookReaderPage(bookId))
                                        if(com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Android) {
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

                                // Bookshelf button for ebooks
                                button {
                                    icon(Icon.collectionsBookmark, "Add to Bookshelf")
                                    onClick {
                                        coordinatorFrame?.openBottomSheet(
                                            halfScreenRatio = 0.7f,
                                            dim = true
                                        ) {
                                            BookshelfPickerDialog(bookId) {
                                                closeThisPopover()
                                            }
                                        }
                                    }
                                }
                            }

                            // Open in browser as secondary option
                            button {
                                row {
                                    gap = 0.5.rem
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

                        // Description section
                        shownWhen { book()?.description != null }.col {
                            gap = 0.5.rem
                            h3 { content = "Description" }
                            text { ::content { book()?.description ?: "" } }
                        }

                        // Chapters section (only for audiobooks)
                        shownWhen {
                            val b = book()
                            b?.itemType == ItemType.AudioBook && (b.chapters.size) > 0
                        }.col {
                            gap = 0.5.rem

                            button {
                                row {
                                    gap = 0.5.rem
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

                            shownWhen { showChapters() }.col {
                                gap = 0.rem

                                // Render chapters with reactive updates using forEachUpdating
                                val chapters = remember {
                                    book()?.chapters ?: emptyList()
                                }


                                // Update chapters signal whenever book changes

                                forEachUpdating(chapters) { chapterReactive ->
                                    col {
                                        gap = 0.rem

                                        button {
                                            row {
                                                gap = 0.75.rem
                                                padding = 0.5.rem

                                                // Chapter index (reactive)
                                                centered.text {
                                                    ::content {
                                                        val chapter = chapterReactive()
                                                        val chapters = book()?.chapters ?: emptyList()
                                                        val idx = chapters.indexOf(chapter) + 1
                                                        "$idx"
                                                    }
                                                }

                                                // Chapter name and duration
                                                expanding.col {
                                                    gap = 0.rem
                                                    text {
                                                        ::content { chapterReactive().name }
                                                        ellipsis = true
                                                    }
                                                    subtext {
                                                        ::content {
                                                            val ticks = chapterReactive().startPositionTicks
                                                            val totalSeconds = ticks / 10_000_000
                                                            val hours = totalSeconds / 3600
                                                            val minutes = (totalSeconds % 3600) / 60
                                                            val seconds = totalSeconds % 60
                                                            if (hours > 0) {
                                                                "$hours:${
                                                                    minutes.toString().padStart(2, '0')
                                                                }:${seconds.toString().padStart(2, '0')}"
                                                            } else {
                                                                "$minutes:${seconds.toString().padStart(2, '0')}"
                                                            }
                                                        }
                                                    }
                                                }

                                                // Current chapter indicator
                                                icon {
                                                    ::shown {
                                                        val chapter = chapterReactive()
                                                        val currentBook = book()
                                                        val chapters = currentBook?.chapters ?: emptyList()
                                                        val chapterIndex = chapters.indexOf(chapter)
                                                        val nextChapterStart =
                                                            chapters.getOrNull(chapterIndex + 1)?.startPositionTicks
                                                                ?: Long.MAX_VALUE

                                                        val position =
                                                            if (currentBook != null && PlaybackState.currentBook()?.id == currentBook.id) {
                                                                PlaybackState.positionTicks()
                                                            } else {
                                                                currentBook?.userData?.playbackPositionTicks ?: 0L
                                                            }
                                                        position >= chapter.startPositionTicks && position < nextChapterStart
                                                    }
                                                    source = Icon.playArrow
                                                    description = "Currently playing"
                                                }
                                            }

                                            onClick {
                                                val currentBook = book()
                                                val chapter = chapterReactive.invoke()
                                                if (currentBook != null) {
                                                    PlaybackState.play(currentBook, chapter.startPositionTicks)
                                                }
                                            }

                                            dynamicTheme {
                                                val chapter = chapterReactive()
                                                val currentBook = book()
                                                val chapters = currentBook?.chapters ?: emptyList()
                                                val chapterIndex = chapters.indexOf(chapter)
                                                val nextChapterStart =
                                                    chapters.getOrNull(chapterIndex + 1)?.startPositionTicks
                                                        ?: Long.MAX_VALUE

                                                val position =
                                                    if (currentBook != null && PlaybackState.currentBook()?.id == currentBook.id) {
                                                        PlaybackState.positionTicks()
                                                    } else {
                                                        currentBook?.userData?.playbackPositionTicks ?: 0L
                                                    }
                                                val isCurrent =
                                                    position >= chapter.startPositionTicks && position < nextChapterStart
                                                if (isCurrent) SelectedSemantic else null
                                            }
                                        }
                                        // Separator
                                        shownWhen {
                                            val chapter = chapterReactive()
                                            val chapters = book()?.chapters ?: emptyList()
                                            val idx = chapters.indexOf(chapter)
                                            idx < chapters.size - 1
                                        }.separator()

                                    }
                                }
                            }
                        }

                        // Bookmarks section
                        val showBookmarks = Signal(false)
                        val bookmarks = Signal(BookmarkRepository.getBookmarksForBook(bookId))

                        shownWhen { bookmarks().isNotEmpty() }.col {
                            gap = 0.5.rem

                            button {
                                row {
                                    gap = 0.5.rem
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
                                gap = 0.rem

                                for (bookmark in bookmarks.value) {
                                    row {
                                        gap = 0.5.rem
                                        padding = 0.5.rem

                                        // Bookmark info
                                        expanding.button {
                                            col {
                                                gap = 0.rem

                                                // Time
                                                text {
                                                    val totalSeconds = bookmark.positionTicks / 10_000_000
                                                    val hours = totalSeconds / 3600
                                                    val minutes = (totalSeconds % 3600) / 60
                                                    val seconds = totalSeconds % 60
                                                    content = if (hours > 0) {
                                                        "$hours:${minutes.toString().padStart(2, '0')}:${
                                                            seconds.toString().padStart(2, '0')
                                                        }"
                                                    } else {
                                                        "$minutes:${seconds.toString().padStart(2, '0')}"
                                                    }
                                                }

                                                // Chapter name if available
                                                bookmark.chapterName?.let { chapterName ->
                                                    subtext {
                                                        content = chapterName
                                                        ellipsis = true
                                                    }
                                                }

                                                // Note if available
                                                bookmark.note?.let { noteText ->
                                                    subtext {
                                                        content = noteText
                                                        ellipsis = true
                                                    }
                                                }
                                            }

                                            onClick {
                                                book()?.let { currentBook ->
                                                    PlaybackState.play(currentBook, bookmark.positionTicks)
                                                }
                                            }
                                        }

                                        // Delete button
                                        button {
                                            icon(Icon.delete, "Delete bookmark")
                                            onClick {
                                                BookmarkRepository.deleteBookmark(bookmark._id)
                                                bookmarks.value = BookmarkRepository.getBookmarksForBook(bookId)
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
        }
    }
}

