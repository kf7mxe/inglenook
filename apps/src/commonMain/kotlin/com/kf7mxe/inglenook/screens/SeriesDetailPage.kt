package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.components.gridListView
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.util.formatBookCount
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.core.Reactive

@Routable("series/{seriesName}")
class SeriesDetailPage(val seriesName: String) : Page {
    override val title: Reactive<String> = Constant(seriesName)

    override fun ViewWriter.render() {
        val books: Reactive<List<Book>> = rememberSuspending {
            val client = jellyfinClient.invoke()
            client?.getBooksBySeries(seriesName) ?: emptyList()
        }
        col {
            // Series header
            row {
                padding = 1.rem
                gap = 1.rem

                // Series cover (first book's cover)
                val cachedSeriesCover = rememberSuspending {
                    val client = jellyfinClient()
                    val firstBook = books().firstOrNull()
                    if (client != null && firstBook?.coverImageId != null) {
                        ImageCache.get(client.getImageUrl(firstBook.coverImageId, firstBook.id))
                    } else null
                }

                sizedBox(SizeConstraints(width = 6.rem, height = 9.rem)).frame {
                    shownWhen { books().isNotEmpty() && books().first().coverImageId != null }.themed(ImageSemantic).image {
                        ::source { cachedSeriesCover() }
                        scaleType = ImageScaleType.Crop
                    }
                }

                // Series info
                expanding.col {
                    gap = 0.25.rem
                    h2 { content = seriesName }
                    subtext {
                        ::content { formatBookCount(books().size) }
                    }

                    shownWhen { books().isNotEmpty() }.subtext {
                        ::content {
                            val allAuthors = books().flatMap { it.authors }.distinct()
                            "by ${allAuthors.map { it.name }.joinToString(", ")}"
                        }
                    }
                }
            }

            separator()

            // Loading state
            shownWhen { !books.state().ready }.centered.inglenookActivityIndicator()

            // Connection error state
            shownWhen { books().isEmpty() && books.state().ready && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(SeriesDetailPage(seriesName))
            }

            // Empty state
            shownWhen { books().isEmpty() && books.state().ready && ConnectivityState.lastNetworkError() == null }.EmptyState(
                icon = Icon.book,
                title = "No books found in this series",
                description = ""
            )

            // Books grid/list
            gridListView(
                items = books,
                keySelector = { it.id },
                gridItem = { bookReactive ->
                    BookCard(
                        book = bookReactive,
                        onPlayClick = { book ->
                            val startPosition = book.userData?.playbackPositionTicks ?: 0L
                            PlaybackState.play(book, startPosition)
                        },
                        onClick = {
                            mainPageNavigator.navigate(BookDetailPage(bookReactive().id))
                        }
                    )
                },
                listItem = { bookReactive ->
                    BookListItem(
                        book = bookReactive,
                        onPlayClick = { book ->
                            val startPosition = book.userData?.playbackPositionTicks ?: 0L
                            PlaybackState.play(book, startPosition)
                        },
                        onClick = {
                            mainPageNavigator.navigate(BookDetailPage(bookReactive().id))
                        }
                    )
                }
            )
        }
    }
}
