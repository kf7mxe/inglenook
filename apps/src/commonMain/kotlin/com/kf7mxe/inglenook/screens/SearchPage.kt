package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.Author
import com.kf7mxe.inglenook.HasId
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.components.bookCard
import com.kf7mxe.inglenook.components.bookListItem
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.searchOff
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.downloads.toAudioBook
import com.kf7mxe.inglenook.jellyfin.SearchResults
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.lastItemViewedScrollToOnBack
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.forEachById
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.childrenMultipleTypes
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.debounceWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SEARCH_DEBOUNCE_MS = 300L

@Routable("/search")
class SearchPage : Page {
    override val title get() = Constant("Search")

    @QueryParameter
    val searchQuery = Signal("")
    override fun ViewWriter.render() {

        val isLoading = Signal(false)


        val searchResults = rememberSuspending {
            val query = searchQuery()
            if(query.isBlank()) return@rememberSuspending emptyList<HasId>()
            if (ConnectivityState.offlineMode.value) {
                val lowerQuery = query.lowercase()
                val matchingBooks = DownloadManager.getDownloads()
                    .filter { download ->
                        download.title.lowercase().contains(lowerQuery) ||
                                download.authors.any { it.name.lowercase().contains(lowerQuery) }
                    }
                    .map { it.toAudioBook() }
//                searchResults.value = SearchResults(books = matchingBooks, authors = emptyList())
                return@rememberSuspending matchingBooks
            }

            try {
                val client = jellyfinClient.value
                if (client != null) {
                    val searchResults = client.search(query)
                    val books = searchResults.books
                    val authors = searchResults.authors
                    return@rememberSuspending books.zip(authors) { book, author -> listOf(book, author) }
                        .flatten() +
                            books.drop(authors.size) +
                            authors.drop(books.size)
                }
            } catch (e: Exception) {
                ConnectivityState.onNetworkError(e.message ?: "Search failed")
            } finally {
                isLoading.value = false
            }
            return@rememberSuspending emptyList()
        }

        col {
            padding = 1.rem
            // Search input
            row {

                centered.icon(Icon.search, "Search")

                expanding.fieldTheme.textInput {
                    hint = "Search books, authors..."
                    content bind searchQuery.debounceWrite(1.seconds)
                }

                shownWhen { searchQuery().isNotBlank() }.button {
                    icon(Icon.close, "Clear")
                    onClick { searchQuery.value = "" }
                }
                viewModeToggleButton()
            }

            separator()

            // Loading indicator
            shownWhen { isLoading() }.centered.col {
                padding = 2.rem
                    inglenookActivityIndicator()
            }

            // Search results
            shownWhen { !isLoading() && searchResults().isNotEmpty() }.expanding.scrolling.col {
                padding = 1.rem
                gap = 1.rem


                expanding.swapView{
                    swapping(
                        current = {
                            viewMode()
                        },
                        views = {viewMode ->
                            when(viewMode) {
                                ViewMode.Grid -> recyclerView {
                                    ::placer { RecyclerViewPlacerVerticalGrid(2) }

                                    childrenMultipleTypes(items = searchResults, id ={ it.id },
                                        renderers = {
                                            elementsMatching { it is Book } renderedAs { book ->
                                                bookCard(book as Reactive<Book>){
                                                    lastItemViewedScrollToOnBack.set(book().id)
                                                    mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                                }
                                            }
                                            elementsMatching { it is Author } renderedAs {author ->
                                                authorCard(author as Reactive<Author>) {
                                                    lastItemViewedScrollToOnBack.set(author().id)
                                                    mainPageNavigator.navigate(AuthorDetailPage(author().id))
                                                }
                                            }
                                        })
                                }
                                ViewMode.List -> recyclerView {
                                    childrenMultipleTypes(items = searchResults, id ={ it.id },
                                        renderers = {
                                            elementsMatching { it is Book } renderedAs { book ->
                                                bookListItem(book as Reactive<Book>) {
                                                    lastItemViewedScrollToOnBack.set(book().id)
                                                    mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                                }
                                            }
                                            elementsMatching { it is Author } renderedAs {author ->
                                                authorListItem(author as Reactive<Author>) {
                                                    lastItemViewedScrollToOnBack.set(author().id)
                                                    mainPageNavigator.navigate(AuthorDetailPage(author().id))
                                                }
                                            }
                                        })
                                }
                            }

                        }
                    )
                }







                // No results message
                shownWhen {
                    searchResults().isEmpty() && searchQuery().isNotBlank()
                }.centered.col {
                    padding = 2.rem
                    gap = 0.5.rem
                    centered.icon(Icon.searchOff, "No results")
                    centered.text("No results found")
                    centered.subtext {
                        ::content { "Try a different search term" }
                    }
                }
            }

            // Empty state (no search yet)
            shownWhen { !isLoading() && searchResults().isEmpty() && searchQuery().isBlank() }.centered.col {
                padding = 2.rem
                gap = 0.5.rem
                centered.icon {
                    source = Icon.search.copy(width = 3.rem, height = 3.rem)
                    description = "Search"
                }
                centered.text("Search your library")
                centered.subtext("Find books by title, author, or narrator")
            }
        }
    }
}
