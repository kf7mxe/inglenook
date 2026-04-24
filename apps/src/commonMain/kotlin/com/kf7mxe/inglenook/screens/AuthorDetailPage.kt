package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.bookCard
import com.kf7mxe.inglenook.components.bookListItem
import com.kf7mxe.inglenook.components.coverImage
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.components.gridListView
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.util.formatBookCount
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.maxHeight
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch

@Routable("authors-detail/{authorId}")
class AuthorDetailPage(val authorId: String) : Page {
    override val title: Reactive<String> = Constant("Author")

    override fun ViewWriter.render() {
        val client = jellyfinClient.value
        val author = rememberSuspending {
            println("DEBUG authorId ${authorId}")
            client?.getAuthor(authorId)
        }
        val books = rememberSuspending {
            client?.getBooksByAuthor(authorId) ?: emptyList()
        }
        col {

            shownWhen { !books.state().ready }.centered.inglenookActivityIndicator()

            // Connection error state
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(AuthorDetailPage(authorId))
            }

            // No books state
            shownWhen { books.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() == null }.EmptyState(
                icon = Icon.book,
                title = "No books found",
                description = "This author has no audiobooks in your library"
            )

            // Author image and info
            row {
                launch{
                    println("DEBUG detail author()?.image ${author()?.imageId}")
                    println("DEBUG detail author()?.id ${authorId}")
                }
                coverImage(
                    imageId = { author()?.imageId },
                    itemId = { authorId },
                    fallbackIcon = Icon.person.copy(width = 4.rem, height = 4.rem),
                    imageHeight = 8.rem,
                    scaleType = ImageScaleType.Crop
                )
                col {
                    h2 { ::content { author()?.name ?: "Unknown Author" } }

                    shownWhen { author()?.overview != null }.maxHeight(height = 9.rem).scrolling.text {
                        ::content { author()?.overview ?: "" }
                    }

                    subtext {
                        ::content { formatBookCount(books().size) }
                    }
                }
            }

            separator()

            // Books section
            gap = 1.rem

            // View mode toggle header
            row {
                expanding.h3 { content = "Books" }
                viewModeToggleButton()
            }

            gap = 1.rem
            gridListView(
                items = books,
                keySelector = { it.id },
                gridItem = { book ->
                    bookCard(book) {
                        mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                    }
                },
                listItem = { book ->
                    bookListItem(book) {
                        mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                    }
                }
            )
        }
    }
}
