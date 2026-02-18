package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending

@Routable("authors-detail/{authorId}")
class AuthorDetailPage(val authorId: String) : Page {
    override val title: Reactive<String> = Constant("Author")

    override fun ViewWriter.render() {
        val client = jellyfinClient.value
        val author = rememberSuspending {
            client?.getAuthor(authorId)
        }
        val books = rememberSuspending {
            client?.getBooksByAuthor(authorId) ?: emptyList()
        }
        col {

            shownWhen { !books.state().ready }.centered.activityIndicator()

            // Connection error state
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(AuthorDetailPage(authorId))
            }

            // No books state
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() == null }.centered.col {
                gap = 0.5.rem
                icon(Icon.book.copy(width = 3.rem, height = 3.rem), "Books")
                text("No books found")
                subtext("This author has no audiobooks in your library")
            }
            // Author image
            val cachedAuthorImage = rememberSuspending {
                val client = jellyfinClient()
                val imageId = author()?.imageId
                if (client != null && imageId != null) {
                    ImageCache.get(client.getImageUrl(imageId, authorId))
                } else null
            }

            row {
                sizeConstraints(width = 8.rem, height = 8.rem).frame {
                    image {
                        this.rView::shown{
                            author()?.imageId != null
                        }
                        ::source {cachedAuthorImage() }
                        scaleType = ImageScaleType.Crop
                    }
                    centered.icon {
                        ::shown{
                            author()?.imageId == null
                        }
                        source = Icon.person.copy(width = 4.rem, height = 4.rem)
                    }

                }
                col {

                    h2 { ::content { author()?.name ?: "Unknown Author" } }

                    // Overview
                    shownWhen { author()?.overview != null }.sizeConstraints(height = 8.rem).scrolling.text {
                        ::content { author()?.overview ?: "" }
                    }

                    subtext {
                        ::content { "${books().size} ${if (books().size == 1) "book" else "books"}" }
                    }
                }
            }


            separator()

            // Books section
                gap = 1.rem

            // View mode toggle header
            row {
                expanding.h3 { content = "Books" }
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


                // Grid view
                    gap = 1.rem
            expanding.swapView {
                swapping(
                    current = {
                        viewMode()
                    },
                    views = { viewMode ->
                        when (viewMode) {
                            ViewMode.Grid -> {
                                expanding.recyclerView {
                                    ::placer{ RecyclerViewPlacerVerticalGrid(2) }
                                    children(books, { it.id }) { book ->
                                        BookCard(book) {
                                            mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                        }
                                    }

                                }
                            }

                            ViewMode.List -> {
                                expanding.recyclerView {
                                    children(books, { it.id }) { book ->
                                        BookListItem(book) {
                                            mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                        }
                                    }
                                }
                            }
                        }
                    })

            }




        }
    }
}
