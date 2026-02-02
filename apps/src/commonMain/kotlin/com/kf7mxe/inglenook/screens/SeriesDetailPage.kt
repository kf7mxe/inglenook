package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.launch

@Routable("series/{seriesName}")
class SeriesDetailPage(val seriesName: String) : Page {
    override val title: Reactive<String> = Constant(seriesName)

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val books = Signal<List<AudioBook>>(emptyList())
        val errorMessage = Signal<String?>(null)

        // Load books in series
        fun loadBooks() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        books.value = client.getBooksBySeries(seriesName)
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load books: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadBooks()

        col {
            // Series header
            row {
                padding = 1.rem
                gap = 1.rem

                // Series cover (first book's cover)
                sizedBox(SizeConstraints(width = 6.rem, height = 9.rem)).frame {
                    shownWhen { books().isNotEmpty() && books().first().coverImageId != null }.image {
                        ::source {
                            val client = jellyfinClient()
                            val firstBook = books().firstOrNull()
                            if (client != null && firstBook?.coverImageId != null) {
                                ImageRemote(client.getImageUrl(firstBook.coverImageId, firstBook.id))
                            } else null
                        }
                        scaleType = ImageScaleType.Crop
                    }
                }

                // Series info
                expanding.col {
                    gap = 0.25.rem
                    h2 { content = seriesName }
                    subtext {
                        ::content {
                            val count = books().size
                            if (count == 1) "1 book" else "$count books"
                        }
                    }

                    // Authors (unique from all books in series)
                    shownWhen { books().isNotEmpty() }.subtext {
                        ::content {
                            val allAuthors = books().flatMap { it.authors }.distinct()
                            "by ${allAuthors.joinToString(", ")}"
                        }
                    }
                }
            }

            separator()

            // Loading state
            shownWhen { isLoading() }.centered.activityIndicator()

            // Error state
            shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                gap = 0.5.rem
                text { ::content { errorMessage() ?: "" } }
                button {
                    text("Retry")
                    onClick { loadBooks() }
                }
            }

            // Empty state
            shownWhen { books().isEmpty() && !isLoading() && errorMessage() == null }.centered.col {
                text { content = "No books found in this series" }
            }

            // Books list
            shownWhen { !isLoading() && errorMessage() == null }.expanding.recyclerView {
                ::placer { RecyclerViewPlacerVerticalGrid(2) }
                children(books, { it.id }) { bookReactive ->
                    BookCard(
                        audioBook = bookReactive,
                        onPlayClick = { book ->
                            val startPosition = book.userData?.playbackPositionTicks ?: 0L
                            PlaybackState.play(book, startPosition)
                        },
                        onClick = {
                            mainPageNavigator.navigate(BookDetailPage(bookReactive().id))
                        }
                    )
                }
            }
        }
    }
}
