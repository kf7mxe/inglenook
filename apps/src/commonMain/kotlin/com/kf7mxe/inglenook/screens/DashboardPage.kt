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
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.downloads.toAudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.direct.scrollsHorizontally
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch

@Routable("/")
class DashboardPage : Page {
    override val title get() = Constant("Home")

    override fun ViewWriter.render() {
        val client = jellyfinClient.value
        val inProgressBooks = rememberSuspending {
            if (ConnectivityState.offlineMode()) emptyList()
            else client?.getInProgressBooks() ?: emptyList()
        }
        val recommendedBooks = rememberSuspending {
            if (ConnectivityState.offlineMode()) emptyList()
            else client?.getSuggestedBooks() ?: emptyList()
        }
        val recentlyAddedBooks = rememberSuspending {
            if (ConnectivityState.offlineMode()) emptyList()
            else client?.getRecentlyAddedBooks() ?: emptyList()
        }

        val downloadedBooks = remember {
            if (ConnectivityState.offlineMode())
                DownloadManager.getDownloads().map { it.toAudioBook() }
            else emptyList()
        }

        val allFinishedLoading = remember {
            inProgressBooks.state().ready && recommendedBooks.state().ready && recentlyAddedBooks.state().ready
        }

        unpadded.scrolling.col {
//            gap = 1.rem

            // Offline: Downloaded Books section
            shownWhen { ConnectivityState.offlineMode() }.col {
                shownWhen { downloadedBooks().isNotEmpty() }.col {
                    row {
                        expanding.h3 { content = "Downloaded Books" }
                        link {
                            text("Manage")
                            to = { DownloadsPage() }
                        }
                    }

                    scrollingHorizontally.row {
                        forEachUpdating(downloadedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                shownWhen { downloadedBooks().isEmpty() }.centered.col {
                    icon(Icon.download.copy(width = 4.rem, height = 4.rem), "Downloads")
                    h3 { content = "No Downloaded Books" }
                    text { content = "Download books while online to listen offline." }
                }
            }

            // Online: normal content
            shownWhen { !ConnectivityState.offlineMode() }.unpadded.col {

                // Continue Listening Section
                col {
                    padded.row {
                        expanding.h3 { content = "Continue Listening" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }
                    shownWhen { !inProgressBooks.state().ready }.activityIndicator { }

                    scrollingHorizontally.padded.row {
                        ::shown {
                            inProgressBooks().isNotEmpty()
                        }
                        forEachUpdating(inProgressBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Recommended For You Section
                col {

                    padded.row {
                        expanding.h3 { content = "Recommended For You" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }

                    shownWhen { !recommendedBooks.state().ready }.activityIndicator { }


                    scrollingHorizontally.padded.row {
                        ::shown{
                            recommendedBooks().isNotEmpty()
                        }
                        forEachUpdating(recommendedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Recently Added Section
                .col {
                    ::shown {
                        recentlyAddedBooks().isNotEmpty()
                    }
                    padded.row {
                        expanding.h3 { content = "Recently Added" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }
                    shownWhen { !recentlyAddedBooks.state().ready }.activityIndicator { }



                    scrollingHorizontally.padded.row {
                        forEachUpdating(recentlyAddedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Empty state when no books
                shownWhen { allFinishedLoading() && inProgressBooks().isEmpty() && recommendedBooks().isEmpty() && recentlyAddedBooks().isEmpty() }.unpadded.centered.col {
                    icon(Icon.book.copy(width = 4.rem, height = 4.rem), "Books")
                    h3 { content = "No Books Found" }
                    text { content = "Your audiobook library appears to be empty." }
                    text { content = "Add some audiobooks to your Jellyfin server to get started." }
                }
            }
        }
    }
}
