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
        val isLoading = Signal(true)
        val client = jellyfinClient.value
        val inProgressBooks= rememberSuspending {
            val test = client?.getInProgressBooks()?:emptyList()
            println("DEBUG test ${test.size}")
            test
        }
        val recommendedBooks= rememberSuspending {
         client?.getSuggestedBooks()?:emptyList()
        }
        val recentlyAddedBooks = rememberSuspending {
            client?.getRecentlyAddedBooks()?:emptyList()
        }

        scrolling.col {
            padding = 1.rem
            gap = 1.5.rem

            // Loading state
//            shownWhen { isLoading() }.centered.activityIndicator()

//

            // Content when loaded
            col {
                gap = 1.5.rem

                // Continue Listening Section
                shownWhen { inProgressBooks().isNotEmpty() }.col {
                    gap = 0.75.rem

                    row {
                        expanding.h3 { content = "Continue Listening" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }

                    scrollingHorizontally.row {
                        gap = 1.rem
                        forEachUpdating(inProgressBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Recommended For You Section
                shownWhen { recommendedBooks().isNotEmpty() }.col {
                    gap = 0.75.rem

                    row {
                        expanding.h3 { content = "Recommended For You" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }

                    scrollingHorizontally.row {
                        gap = 1.rem
                        forEachUpdating(recommendedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Recently Added Section
                shownWhen { recentlyAddedBooks().isNotEmpty() }.col {
                    gap = 0.75.rem

                    row {
                        expanding.h3 { content = "Recently Added" }
                        link {
                            text("See All")
                            to = { LibraryPage() }
                        }
                    }

                    scrollsHorizontally.row {
                        gap = 1.rem
                        forEachUpdating(recentlyAddedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                            }
                        }
                    }
                }

                // Empty state when no books
                shownWhen { inProgressBooks().isEmpty() && recommendedBooks().isEmpty() && recentlyAddedBooks().isEmpty() }.centered.col {
                    padding = 2.rem
                    gap = 1.rem
                    icon(Icon.book.copy(width = 4.rem, height = 4.rem), "Books")
                    h3 { content = "No Books Found" }
                    text { content = "Your audiobook library appears to be empty." }
                    text { content = "Add some audiobooks to your Jellyfin server to get started." }
                }
            }
        }
    }
}
