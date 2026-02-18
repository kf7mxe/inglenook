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
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.searchOff
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.downloads.toAudioBook
import com.kf7mxe.inglenook.jellyfin.SearchResults
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Routable("/search")
class SearchPage : Page {
    override val title get() = Constant("Search")

    override fun ViewWriter.render() {
        val searchQuery = Signal("")
        val isLoading = Signal(false)
        val searchResults = Signal<SearchResults?>(null)
        var searchJob: Job? = null

        fun performSearch(query: String) {
            searchJob?.cancel()
            if (query.isBlank()) {
                searchResults.value = null
                return
            }

            if (ConnectivityState.offlineMode.value) {
                val lowerQuery = query.lowercase()
                val matchingBooks = DownloadManager.getDownloads()
                    .filter { download ->
                        download.title.lowercase().contains(lowerQuery) ||
                        download.authors.any { it.name.lowercase().contains(lowerQuery) }
                    }
                    .map { it.toAudioBook() }
                searchResults.value = SearchResults(books = matchingBooks, authors = emptyList())
                return
            }

            searchJob = AppScope.launch {
                // Debounce - wait before searching
                delay(300)

                isLoading.value = true
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        searchResults.value = client.search(query)
                    }
                } catch (e: Exception) {
                    ConnectivityState.onNetworkError(e.message ?: "Search failed")
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Watch for search query changes
        searchQuery.addListener {
            performSearch(searchQuery.value)
        }

        col {
            gap = 0.rem

            // Search input
            row {
                padding = 1.rem
                gap = 0.5.rem

                centered.icon(Icon.search, "Search")

                expanding.textInput {
                    hint = "Search books, authors..."
                    content bind searchQuery
                }

                shownWhen { searchQuery().isNotBlank() }.button {
                    icon(Icon.close, "Clear")
                    onClick { searchQuery.value = "" }
                }
            }

            separator()

            // Loading indicator
            shownWhen { isLoading() }.centered.col {
                padding = 2.rem
                activityIndicator()
            }

            // Search results
            shownWhen { !isLoading() && searchResults() != null }.expanding.scrolls.col {
                padding = 1.rem
                gap = 1.rem


                // Books section
                shownWhen { searchResults()?.books?.isNotEmpty() == true }.col {
                    gap = 0.5.rem

                    h3 { content = "Books" }

                    col {
                        gap = 0.rem
                        val books =  remember {searchResults()?.books ?: emptyList() }
                        forEach(books) {book ->
                            col {
                                bookSearchResult(book)
                                separator()
                            }
                        }
                    }
                }

                // Authors section
                shownWhen { searchResults()?.authors?.isNotEmpty() == true }.col {
                    gap = 0.5.rem

                    h3 { content = "Authors" }

                    col {
                        gap = 0.rem
                        val authors = remember { searchResults()?.authors ?: emptyList() }
                        forEach(authors) { author ->
                            col {
                                authorSearchResult(author)
                                separator()
                            }
                            }
                    }
                }

                // No results message
                shownWhen {
                    searchResults() != null &&
                    searchResults()?.books?.isEmpty() == true &&
                    searchResults()?.authors?.isEmpty() == true
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
            shownWhen { !isLoading() && searchResults() == null && searchQuery().isBlank() }.centered.col {
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

    private fun ViewWriter.bookSearchResult(book: Book) {
        val cachedCover = rememberSuspending {
            val client = jellyfinClient.value
            if (client != null && book.coverImageId != null) {
                ImageCache.get(client.getImageUrl(book.coverImageId, book.id))
            } else null
        }

        button {
            row {
                gap = 0.75.rem
                padding = 0.5.rem

                // Cover image
                sizeConstraints(width = 3.rem, height = 4.rem).frame {
                    shownWhen { book.coverImageId != null }.image {
                        ::source { cachedCover() }
                        scaleType = ImageScaleType.Crop
                    }
                    shownWhen { book.coverImageId == null }.centered.icon(Icon.book, "Book")
                }

                // Book info
                expanding.col {
                    gap = 0.rem
                    text {
                        content = book.title
                        ellipsis = true
                    }
                    subtext {
                        content = book.authors.joinToString(", ").ifEmpty { "Unknown Author" }
                        ellipsis = true
                    }
                    shownWhen { book.seriesName != null }.subtext {
                        content = if (book.indexNumber != null) {
                            "${book.seriesName} #${book.indexNumber}"
                        } else {
                            book.seriesName ?: ""
                        }
                    }
                }

                centered.icon(Icon.chevronRight, "View")
            }

            onClick {
                mainPageNavigator.navigate(BookDetailPage(book.id))
            }
        }
    }

    private fun ViewWriter.authorSearchResult(author: Author) {
        val cachedAuthorImage = rememberSuspending {
            val client = jellyfinClient.value
            if (client != null && author.imageId != null) {
                ImageCache.get(client.getImageUrl(author.imageId, author.id))
            } else null
        }

        button {
            row {
                gap = 0.75.rem
                padding = 0.5.rem

                // Author image
                sizeConstraints(width = 3.rem, height = 3.rem).frame {
                    shownWhen { author.imageId != null }.image {
                        ::source { cachedAuthorImage() }
                        scaleType = ImageScaleType.Crop
                    }
                    shownWhen { author.imageId == null }.centered.icon(Icon.person, "Author")
                }

                // Author info
                expanding.col {
                    gap = 0.rem
                    text {
                        content = author.name
                        ellipsis = true
                    }
                    shownWhen { author.overview != null }.subtext {
                        content = author.overview?.take(100) ?: ""
                        ellipsis = true
                    }
                }

                centered.icon(Icon.chevronRight, "View")
            }

            onClick {
                mainPageNavigator.navigate(AuthorDetailPage(author.id))
            }
        }
    }
}
