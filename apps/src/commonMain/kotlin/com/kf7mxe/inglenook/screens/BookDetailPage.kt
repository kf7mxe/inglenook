package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.components.downloadButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.lightningkite.reactive.context.invoke
import com.kf7mxe.inglenook.components.BookshelfPickerDialog
import com.kf7mxe.inglenook.components.IdentifyDialog
import com.kf7mxe.inglenook.components.bookmarksList
import com.kf7mxe.inglenook.components.chaptersList
import com.kf7mxe.inglenook.ebook.ebookReader
import com.lightningkite.kiteui.views.l2.coordinatorFrame
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.util.calculateProgressPercent
import com.kf7mxe.inglenook.util.calculateProgressRatio
import com.kf7mxe.inglenook.util.formatDurationShort
import com.kf7mxe.inglenook.util.truncateDisplay
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.card
import com.lightningkite.reactive.core.Signal
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

    val cachedCover = rememberSuspending {
        val currentBook = book()
        jellyfinClient().fetchCoverImage(currentBook?.coverImageId, currentBook?.id)
    }

    override fun ViewWriter.render() {
        val showChapters = Signal(false)

        // Scrollable content
        padded.expanding.scrolling.col {
            paddingByEdge = Edges(0.rem,1.rem)
            // Connection error state (book failed to load due to network)
            shownWhen { book.state().ready && book() == null && ConnectivityState.lastNetworkError() != null }.expanding.connectionError {
                mainPageNavigator.navigate(BookDetailPage(bookId))
            }

                row {


//                        sizeConstraints(height = 15.rem).frame {
                    themed(ImageSemantic).sizeConstraints(height = 15.rem).image {
//                                this.rView::shown{
//                                    cachedCover() != null
//                                }
                        ::source { cachedCover() }
                        scaleType = ImageScaleType.Fit

                    }
//                        }
//                            shownWhen { book()?.coverImageId == null }.centered.icon {
//                                ::shown {
//                                    cachedCover() == null
//                                }
//                                source = Icon.book.copy(width = 4.rem, height = 4.rem)
//                                description = "Book cover"
//                            }


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

                        // Duration (audiobooks only)
                        subtext {
                            ::shown{
                                book()?.itemType == ItemType.AudioBook
                            }
                            ::content {
                                formatDurationShort(book()?.duration ?: 0L)
                            }
                        }

                        // Completed indicator
                        shownWhen { book()?.userData?.played == true }.row {
                            icon(Icon.checkCircle, "Completed")
                            subtext { content = "Completed" }
                        }

                        // Progress indicator (in progress, not completed)
                        shownWhen {
                            val b = book()
                            (b?.userData?.playbackPositionTicks ?: 0L) > 0L && b?.userData?.played != true
                        }.padded.col {
                            progressBar {
                                ::ratio {
                                    val b = book()
                                    val position = b?.userData?.playbackPositionTicks ?: 0L
                                    val dur = b?.duration ?: 1L
                                    calculateProgressRatio(position, dur)
                                }
                            }
                            subtext {
                                ::content {
                                    val b = book()
                                    val position = b?.userData?.playbackPositionTicks ?: 0L
                                    val dur = b?.duration ?: 1L
                                    "${calculateProgressPercent(position, dur)}% complete"
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
                    buttonTheme.downloadButton(book)

                    buttonTheme.menuButton {
                        icon(Icon.moreVert, "More")
                        opensMenu {
                            col {

                                bookshelfButton(bookId)
                                // Identify button (search remote metadata providers)
                                buttonTheme.button {
                                    row {
                                        centered.icon(Icon.search, "Identify")
                                        centered.text { content = "Identify" }
                                    }
                                    onClick {
                                        val currentTitle = book()?.title ?: ""
                                        coordinatorFrame?.bottomSheet(
                                            partialRatio = 0.85f,
                                            startState = BottomSheetState.PARTIALLY_EXPANDED
                                        ) { control ->
                                            unpadded.IdentifyDialog(
                                                bookId = bookId,
                                                bookTitle = currentTitle,
                                                onApplied = {
                                                    // Refresh book data after metadata is applied
                                                    control.close()
                                                    mainPageNavigator.navigate(BookDetailPage(bookId))
                                                },
                                                onDismiss = {
                                                    control.close()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                        }
                    }



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
                                openEbook(bookId, this@button)
                            }
                            themeChoice += ImportantSemantic
                        }
                        downloadButton(book)

                        buttonTheme.menuButton {
                            icon(Icon.moreVert, "More")
                            opensMenu {
                                col {
                                    bookshelfButton(bookId)
                                    // Identify button (search remote metadata providers)
                                    buttonTheme.button {
                                        row {
                                            centered.icon(Icon.search, "Identify")
                                            centered.text { content = "Identify" }
                                        }
                                        onClick {
                                            val currentTitle = book()?.title ?: ""
                                            coordinatorFrame?.bottomSheet(
                                                partialRatio = 0.85f,
                                                startState = BottomSheetState.PARTIALLY_EXPANDED
                                            ) { control ->
                                                unpadded.IdentifyDialog(
                                                    bookId = bookId,
                                                    bookTitle = currentTitle,
                                                    onApplied = {
                                                        control.close()
                                                        mainPageNavigator.navigate(BookDetailPage(bookId))
                                                    },
                                                    onDismiss = {
                                                        control.close()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                val memberBookshelves = rememberSuspending {
                    BookshelfRepository.getBookshelvesContainingBook(bookId)
                }
                shownWhen { memberBookshelves()?.isNotEmpty() == true }.col {
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
                }.button {
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

                // Bookmarks section
                val showBookmarks = Signal(false)
                val bookmarks = Signal(BookmarkRepository.getBookmarksForBook(bookId))

                shownWhen { bookmarks().isNotEmpty() }.button {
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

suspend fun openEbook(bookId: String, vw: ViewWriter) {
    if (com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Web) vw.mainPageNavigator.navigate(
        EbookReaderPage(bookId)
    )
    if (com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Android) {
        val client = jellyfinClient.value
        if (client != null) {
            val downloadUrl = client.getEbookDownloadUrl(bookId)
            val authHeader = client.getAuthHeader()
            vw.ebookReader(bookId, downloadUrl, authHeader)
//            vw.mainPageNavigator.navigate(EbookReaderPage(bookId))
        }
    }
}

private fun ViewWriter.bookshelfButton(bookId: String) {
    buttonTheme.button {
        row {
            icon(Icon.collectionsBookmark, "Add to Bookshelf")
            text("Add to Bookshelf")
        }
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
