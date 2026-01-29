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
import com.kf7mxe.inglenook.Author
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.launch

@Routable("authors-detail/{authorId}")
class AuthorDetailPage(val authorId: String) : Page {
    override val title: Reactive<String> = Constant("Author")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val author = Signal<Author?>(null)
        val books = Signal<List<AudioBook>>(emptyList())
        val viewMode = Signal(ViewMode.Grid)
        val errorMessage = Signal<String?>(null)

        // Load author and their books
        fun loadData() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        author.value = client.getAuthor(authorId)
                        books.value = client.getBooksByAuthor(authorId)
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load author: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadData()

        col {
            gap = 0.rem

            // View mode toggle header
            shownWhen { !isLoading() && books().isNotEmpty() }.row {
                padding = 1.rem
                expanding.space(1.0)
                button {
                    icon {
                        ::source { if (viewMode() == ViewMode.Grid) Icon.menu else Icon.dashboard }
                        description = "Toggle view"
                    }
                    onClick {
                        viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                    }
                }
            }

            expanding.scrolls.col {
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
                        onClick { loadData() }
                    }
                }

                // Content
                shownWhen { !isLoading() && errorMessage() == null }.col {
                    gap = 1.5.rem

                    // Author header
                    centered.col {
                        gap = 0.75.rem

                        // Author image
                        sizedBox(SizeConstraints(width = 8.rem, height = 8.rem)).frame {
                            val currentAuthor = author.value
                            if (currentAuthor?.imageId != null) {
                                val client = jellyfinClient.value
                                image {
                                    source = ImageRemote(client?.getImageUrl(currentAuthor.imageId) ?: "")
                                    scaleType = ImageScaleType.Crop
                                }
                            } else {
                                centered.icon(Icon.person.copy(width = 4.rem, height = 4.rem), "Author")
                            }
                        }

                        h2 { ::content { author()?.name ?: "Unknown Author" } }

                        // Overview
                        shownWhen { author()?.overview != null }.text {
                            ::content { author()?.overview ?: "" }
                        }

                        subtext {
                            ::content { "${books().size} ${if (books().size == 1) "book" else "books"}" }
                        }
                    }

                    separator()

                    // Books section
                    shownWhen { books().isNotEmpty() }.col {
                        gap = 1.rem

                        h3 { content = "Books" }

                        // Grid view
                        shownWhen { viewMode() == ViewMode.Grid }.row {
                            gap = 1.rem
                            forEach(books) { book ->
                                BookCard(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                            }
                        }

                        // List view
                        shownWhen { viewMode() == ViewMode.List }.col {
                            gap = 0.5.rem
                            forEach(books) { book ->
                                BookListItem(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                                separator()
                            }
                        }
                    }

                    // No books state
                    shownWhen { books().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        icon(Icon.book.copy(width = 3.rem, height = 3.rem), "Books")
                        text("No books found")
                        subtext("This author has no audiobooks in your library")
                    }
                }
            }
        }
    }
}
