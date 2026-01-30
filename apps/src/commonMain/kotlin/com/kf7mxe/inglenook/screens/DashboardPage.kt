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
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import kotlinx.coroutines.launch

@Routable("/dashboard")
class DashboardPage : Page {
    override val title get() = Constant("Home")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val inProgressBooks = Signal<List<AudioBook>>(emptyList())
        val recommendedBooks = Signal<List<AudioBook>>(emptyList())
        val recentlyAddedBooks = Signal<List<AudioBook>>(emptyList())
        val errorMessage = Signal<String?>(null)

        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    // Load in-progress books
                    inProgressBooks.value = client.getInProgressBooks()
                    // Load recommended books
                    recommendedBooks.value = client.getSuggestedBooks()
                    // Load recently added books
                    recentlyAddedBooks.value = client.getRecentlyAddedBooks()
                }
            } catch (e: Exception) {
                errorMessage.value = "Failed to load books: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }

        scrolls.col {
            padding = 1.rem
            gap = 1.5.rem

            // Loading state
            shownWhen { isLoading() }.centered.activityIndicator()

            // Error state
            shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                gap = 0.5.rem
                text { ::content { errorMessage() ?: "" } }
                button {
                    text("Retry")
                    onClick {
                        isLoading.value = true
                        errorMessage.value = null
                        AppScope.launch {
                            try {
                                val client = jellyfinClient.value
                                if (client != null) {
                                    inProgressBooks.value = client.getInProgressBooks()
                                    recommendedBooks.value = client.getSuggestedBooks()
                                    recentlyAddedBooks.value = client.getRecentlyAddedBooks()
                                }
                            } catch (e: Exception) {
                                errorMessage.value = "Failed to load books: ${e.message}"
                            } finally {
                                isLoading.value = false
                            }
                        }
                    }
                }
            }

            // Content when loaded
            shownWhen { !isLoading() && errorMessage() == null }.col {
                gap = 1.5.rem

                // Continue Listening Section
                shownWhen { inProgressBooks().isNotEmpty() }.col {
                    gap = 0.75.rem

                    row {
                        expanding.h3 { content = "Continue Listening" }
                        link {
                            text("See All")
                            to = { BooksPage() }
                        }
                    }

                    scrollingHorizontally.row {
                        gap = 1.rem
                        forEach(inProgressBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.id))
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
                            to = { BooksPage() }
                        }
                    }

                    scrollingHorizontally.row {
                        gap = 1.rem
                        forEach(recommendedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.id))
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
                            to = { BooksPage() }
                        }
                    }

                    scrollsHorizontally.row {
                        gap = 1.rem
                        forEach(recentlyAddedBooks) { book ->
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.id))
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
