package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.bookCard
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.downloads.toAudioBook
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending

@Routable("/home")
class HomePage : Page {
    override val title get() = Constant("Home")

    override fun ViewWriter.render() {
        val inProgressBooks = rememberSuspending {
            ConnectivityState.offlineMode() // Reactive dependency — reloads when connectivity changes
            jellyfinClient()?.getInProgressBooks() ?: emptyList()
        }
        val recommendedBooks = rememberSuspending {
            ConnectivityState.offlineMode()
            jellyfinClient()?.getSuggestedBooks() ?: emptyList()
        }
        val recentlyAddedBooks = rememberSuspending {
            ConnectivityState.offlineMode()
            jellyfinClient()?.getRecentlyAddedBooks() ?: emptyList()
        }

        val downloadedBooks = remember {
            DownloadManager.getDownloads().map { it.toAudioBook() }
        }

        val allFinishedLoading = remember {
            inProgressBooks.state().ready && recommendedBooks.state().ready && recentlyAddedBooks.state().ready
        }

        unpadded.scrolling.col {
//            gap = 1.rem

            // Banner: server reachable while in manual offline mode
            shownWhen { ConnectivityState.offlineMode() && ConnectivityState.serverReachable() }.col {
                padding = 1.rem
                card.row {
                    expanding.col {
                        gap = 0.rem
                        text("Server available")
                        subtext("Your Jellyfin server is reachable.")
                    }
                    button {
                        text("Go Online")
                        onClick {
                            ConnectivityState.exitOfflineMode()
                        }
                        themeChoice += ImportantSemantic
                    }
                }
            }

            // Downloaded Books section (shown when offline or has downloads)
            shownWhen { ConnectivityState.offlineMode() && downloadedBooks().isNotEmpty() }.col {
                padded.col {
                    row {
                        expanding.h3 { content = "Downloaded Books" }
                        link {
                            text("Manage")
                            to = { DownloadsPage() }
                        }
                    }

                    scrollingHorizontally.row {
                        forEachUpdating(downloadedBooks) { book ->
                            bookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                shownWhen { downloadedBooks().isEmpty() && ConnectivityState.offlineMode() }.centered.col {
                    icon(Icon.download.copy(width = 4.rem, height = 4.rem), "Downloads")
                    h3 { content = "No Downloaded Books" }
                    text { content = "Download books while online to listen offline." }
                }
            }

            // Main content sections (shown online, or offline with cached data)
            unpadded.col {

                // Continue Listening Section
                col {
                    ::shown { inProgressBooks().isNotEmpty() || !inProgressBooks.state().ready }
                    padded.row {
                        expanding.h3 { content = "Continue Listening" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }

                    shownWhen { !inProgressBooks.state().ready }.inglenookActivityIndicator()

                    scrollingHorizontally.padded.row {
                        ::shown {
                            inProgressBooks().isNotEmpty()
                        }
                        forEachUpdating(inProgressBooks) { book ->
                            bookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Recommended For You Section
                col {
                    ::shown { recommendedBooks().isNotEmpty() || !recommendedBooks.state().ready }

                    padded.row {
                        expanding.h3 { content = "Recommended For You" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }

                    shownWhen { !recommendedBooks.state().ready }.inglenookActivityIndicator()


                    scrollingHorizontally.padded.row {
                        ::shown{
                            recommendedBooks().isNotEmpty()
                        }
                        forEachUpdating(recommendedBooks) { book ->
                            bookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Recently Added Section
                col {
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
                    shownWhen { !recentlyAddedBooks.state().ready }.inglenookActivityIndicator()



                    scrollingHorizontally.padded.row {
                        forEachUpdating(recentlyAddedBooks) { book ->
                            bookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Connection error state (show when everything is empty and we have a network error)
                shownWhen { allFinishedLoading() && inProgressBooks().isEmpty() && recommendedBooks().isEmpty() && recentlyAddedBooks().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                    mainPageNavigator.navigate(HomePage())
                }

                // Empty state when no books and no network error (library is genuinely empty)
                shownWhen { allFinishedLoading() && inProgressBooks().isEmpty() && recommendedBooks().isEmpty() && recentlyAddedBooks().isEmpty() && ConnectivityState.lastNetworkError() == null && !ConnectivityState.offlineMode() }.unpadded.centered.col {
                    icon(Icon.book.copy(width = 4.rem, height = 4.rem), "Books")
                    h3 { content = "No Books Found" }
                    text { content = "Your audiobook library appears to be empty." }
                    text { content = "Add some audiobooks to your Jellyfin server to get started." }
                }
            }
        }
    }
}
