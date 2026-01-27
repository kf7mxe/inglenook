package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class AuthorDetailPage(val authorId: String) : Page {
    override val title: ReactiveContext.() -> String = { "" }

    override fun ViewWriter.render() {
        val author = Signal<Author?>(null)
        val books = Signal<List<AudioBook>>(emptyList())
        val viewMode = Signal(ViewMode.Grid)
        val isLoading = Signal(true)

        // Load author and their books
        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    author.value = client.getAuthor(authorId)
                    books.value = client.getBooksByAuthor(authorId)
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

        scrolls.col {
            padding = 1.rem
            gap = 1.rem

            shownWhen { isLoading() }.centered.activityIndicator()

            shownWhen { !isLoading() && author() == null }.centered.col {
                gap = 0.5.rem
                text("Author not found")
                button {
                    text("Go Back")
                    onClick { mainPageNavigator.goBack() }
                }
            }

            shownWhen { !isLoading() && author() != null }.col {
                gap = 1.5.rem

                // Author header
                centered.col {
                    gap = 0.5.rem

                    // Author image or placeholder
                    sizedBox(SizeConstraints(width = 6.rem, height = 6.rem)) {
                        themeChoice = CircleSemantic.onNext
                        val a = author()
                        if (a?.imageId != null) {
                            val client = jellyfinClient.value
                            image {
                                source = ImageRemote(client?.getImageUrl(a.imageId) ?: "")
                                scaleType = ImageScaleType.Crop
                            }
                        } else {
                            centered.icon(Icon.person, "Author")
                        }
                    }

                    h2 {
                        ::content { author()?.name ?: "" }
                    }

                    subtext {
                        ::content { "${books().size} books" }
                    }
                }

                // Author bio if available
                shownWhen { !author()?.overview.isNullOrBlank() }.col {
                    gap = 0.5.rem
                    h3 { content = "About" }
                    text {
                        ::content { author()?.overview ?: "" }
                    }
                }

                // Books by author
                col {
                    gap = 0.5.rem

                    row {
                        expanding.h3 { content = "Books" }
                        button {
                            icon {
                                ::source { if (viewMode() == ViewMode.Grid) Icon.viewList else Icon.viewModule }
                            }
                            onClick {
                                viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                            }
                        }
                    }

                    shownWhen { books().isEmpty() }.text {
                        content = "No books found"
                    }

                    shownWhen { books().isNotEmpty() && viewMode() == ViewMode.Grid }.col {
                        reactiveSuspending {
                            clearChildren()
                            val chunked = books().chunked(3)
                            for (rowBooks in chunked) {
                                row {
                                    gap = 1.rem
                                    for (book in rowBooks) {
                                        expanding.BookCard(book) {
                                            mainPageNavigator.navigate(BookDetailPage(book.id))
                                        }
                                    }
                                    repeat(3 - rowBooks.size) {
                                        expanding.space(1.0)
                                    }
                                }
                            }
                        }
                    }

                    shownWhen { books().isNotEmpty() && viewMode() == ViewMode.List }.col {
                        reactiveSuspending {
                            clearChildren()
                            for (book in books()) {
                                BookListItem(book) {
                                    mainPageNavigator.navigate(BookDetailPage(book.id))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
