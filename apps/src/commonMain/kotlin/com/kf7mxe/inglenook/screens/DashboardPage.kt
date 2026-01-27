package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.applySafeInsets
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class DashboardPage(val unit: Unit = Unit) : Page {
    override val title: ReactiveContext.() -> String = { "Home" }

    override fun ViewWriter.render() {
        val continueListening = Signal<List<AudioBook>>(emptyList())
        val recentlyAdded = Signal<List<AudioBook>>(emptyList())
        val isLoading = Signal(true)

        // Load data when page loads
        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    // Get books with progress (continue listening)
                    val inProgress = client.getInProgressBooks()
                    continueListening.value = inProgress

                    // Get recently added books
                    val recent = client.getRecentlyAddedBooks()
                    recentlyAdded.value = recent
                }
            } catch (e: Exception) {
                // Handle error - show message
            } finally {
                isLoading.value = false
            }
        }

        scrolls.col {
            padding = 1.rem
            gap = 1.5.rem

            // Continue Listening section
            col {
                gap = 0.5.rem
                h2 { content = "Continue Listening" }

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() && continueListening().isEmpty() }.text {
                    content = "No books in progress"
                    themeChoice = ThemeDerivation { it.copy(foreground = it.foreground.applyAlpha(0.6f)).withoutBack }.onNext
                }

                shownWhen { !isLoading() && continueListening().isNotEmpty() }.scrollsHorizontally.row {
                    gap = 1.rem
                    ::exists { continueListening().isNotEmpty() }

                    reactiveSuspending {
                        clearChildren()
                        for (book in continueListening()) {
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.id))
                            }
                        }
                    }
                }
            }

            // Recently Added section
            col {
                gap = 0.5.rem
                h2 { content = "Recently Added" }

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() && recentlyAdded().isEmpty() }.text {
                    content = "No recent books"
                    themeChoice = ThemeDerivation { it.copy(foreground = it.foreground.applyAlpha(0.6f)).withoutBack }.onNext
                }

                shownWhen { !isLoading() && recentlyAdded().isNotEmpty() }.scrollsHorizontally.row {
                    gap = 1.rem
                    ::exists { recentlyAdded().isNotEmpty() }

                    reactiveSuspending {
                        clearChildren()
                        for (book in recentlyAdded()) {
                            BookCard(book) {
                                mainPageNavigator.navigate(BookDetailPage(book.id))
                            }
                        }
                    }
                }
            }

            // Quick access to library
            col {
                gap = 0.5.rem
                h2 { content = "Browse Library" }

                row {
                    gap = 1.rem

                    button {
                        col {
                            gap = 0.25.rem
                            centered.icon(Icon.book, "Books")
                            centered.text("All Books")
                        }
                        onClick {
                            mainPageNavigator.navigate(BooksPage())
                        }
                    }

                    button {
                        col {
                            gap = 0.25.rem
                            centered.icon(Icon.person, "Authors")
                            centered.text("Authors")
                        }
                        onClick {
                            mainPageNavigator.navigate(AuthorsPage())
                        }
                    }
                }
            }
        }
    }
}
