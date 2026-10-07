package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.HasId
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.ThemePreset
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.bookCard
import com.kf7mxe.inglenook.components.bookListItem
import com.kf7mxe.inglenook.components.emptyState
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.currentThemePreset
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.lastItemViewedScrollToOnBack
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.Edges
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImportantSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

private const val PAGE_SIZE = 50

private val IN_PROGRESS_FILTER = FilterOption("in-progress", "In Progress") {
    (it.userData?.playbackPositionTicks ?: 0L) > 0L && it.userData?.played != true
}

private val FAVORITES_FILTER = FilterOption("favorites", "Favorites") {
    it.userData?.isFavorite == true
}

class BooksPage(
    val selectedFilter: Signal<FilterOption?> = Signal(null),
    val bookTypeFilter: Signal<ItemType?> = Signal(null)
) : Page {
    override val title get() = Constant("Books")


    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        val books = Signal<List<Book>>(emptyList())
        val nextStartIndex = Signal(0)
        val isLoading = Signal(false)
        val hasMore = Signal(true)
        val initialLoad = rememberSuspending {
            ConnectivityState.offlineMode()
            isLoading.value = true
            try {
                val page = jellyfinClient()?.getBooksPage(startIndex = 0, limit = PAGE_SIZE)
                books.value = page?.books ?: emptyList()
                nextStartIndex.value = page?.books?.size ?: 0
                hasMore.value = page != null && page.books.size >= PAGE_SIZE &&
                        (page.totalCount == 0 || nextStartIndex.value < page.totalCount)
                true
            } finally {
                isLoading.value = false
            }
        }

        val loadNextPage: suspend () -> Unit = suspend loadNextPage@{
            if (isLoading() || !hasMore()) return@loadNextPage
            val client = jellyfinClient() ?: return@loadNextPage
            isLoading.set(true)
            try {
                val page = client.getBooksPage(nextStartIndex(), PAGE_SIZE)
                val existingIds = books().asSequence().map { it.id }.toHashSet()
                books.value = books() + page.books.filter { it.id !in existingIds }
                nextStartIndex.value += page.books.size
                hasMore.value = page.books.size >= PAGE_SIZE &&
                        (page.totalCount == 0 || nextStartIndex.value < page.totalCount)
            } finally {
                isLoading.set(false)
            }
        }

        val filteredBooks: Reactive<List<Book>> = remember {
            val filter = selectedFilter()
            var result = books()

            if (filter != null) {
                result = result.filter(filter.filterFn)
            }

            val typeFilter = bookTypeFilter()
            if (typeFilter != null) {
                result = result.filter { it.itemType == typeFilter }
            }

            result.sortedBy { it.title.lowercase() }
        }

        col {
            // Book filters and view toggle
            row {
                card.button {
                    text("All")
                    onClick {
                        selectedFilter.value = null
                        bookTypeFilter.value = null
                    }
                    dynamicTheme { if (selectedFilter() == null && bookTypeFilter() == null) ImportantSemantic else null }
                }
                card.button {
                    text("In Progress")
                    onClick { selectedFilter.value = IN_PROGRESS_FILTER }
                    dynamicTheme { if (selectedFilter()?.id == IN_PROGRESS_FILTER.id) ImportantSemantic else null }
                }
                card.button {
                    text("Favorites")
                    onClick { selectedFilter.value = FAVORITES_FILTER }
                    dynamicTheme { if (selectedFilter()?.id == FAVORITES_FILTER.id) ImportantSemantic else null }
                }

                // Book type filter toggles
                card.button {
                    text("Audio")
                    onClick { bookTypeFilter.value = ItemType.AudioBook }
                    dynamicTheme { if (bookTypeFilter() == ItemType.AudioBook) ImportantSemantic else null }
                }
                card.button {
                    text("Ebooks")
                    onClick { bookTypeFilter.value = ItemType.Ebook }
                    dynamicTheme { if (bookTypeFilter() == ItemType.Ebook) ImportantSemantic else null }
                }

                viewModeToggleButton()
            }
            sizeConstraints(height = 0.02.rem).frame() {
                ::shown {
                    currentThemePreset() == ThemePreset.NeumorphismLight || currentThemePreset() == ThemePreset.NeumorphismDark
                }
            }

            shownWhen { !initialLoad.state().ready }.inglenookActivityIndicator()

            // Connection error state
            shownWhen { initialLoad.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Empty library state
            shownWhen { initialLoad.state().ready && books().isEmpty() && ConnectivityState.lastNetworkError() == null }.emptyState(
                icon = Icon.book,
                title = "No books found",
                description = "Your audiobook library is empty"
            )

            // Empty filter state
            shownWhen { initialLoad.state().ready && books().isNotEmpty() && filteredBooks().isEmpty() }.centered.col {
                gap = 0.5.rem
                icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Filter")
                text("No books match this filter")
                subtext { ::content { "Try a different filter or select All" } }
            }


            expanding.swapView {
                swapping(
                    current = { viewMode() },
                    views = { mode ->
                        val scrollTo = remember {
                            if (lastItemViewedScrollToOnBack() == null) return@remember 0
                            filteredBooks().indexOfFirst { (it as HasId).id == lastItemViewedScrollToOnBack() }.takeIf { it != -1 }
                                ?: return@remember 0
                        }

                        when (mode) {
                            ViewMode.Grid -> {
                                expanding.recyclerView {
                                    ::placer { RecyclerViewPlacerVerticalGrid(2) }
                                    launch {
                                        if (lastItemViewedScrollToOnBack() == null) return@launch
                                        scrollToIndex(scrollTo(), Align.Start, false)
                                    }
                                     children(filteredBooks, {it.id}) { book ->
                                         bookCard(book) {
                                             lastItemViewedScrollToOnBack.set(book().id)
                                             mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                         }
                                     }
                                     reactive {
                                         if (hasMore() && (filteredBooks().isEmpty() ||
                                             (filteredBooks().isNotEmpty() && lastIndex() >= filteredBooks().lastIndex - 10))) {
                                             launch { loadNextPage() }
                                         }
                                     }

                                }
                            }

                            ViewMode.List -> {
                                expanding.recyclerView {
                                    launch {
                                        if (lastItemViewedScrollToOnBack() == null) return@launch
                                        scrollToIndex(scrollTo(), Align.Start, false)
                                    }
                                     children(filteredBooks, { it.id }) { book ->
                                         bookListItem(book) {
                                             lastItemViewedScrollToOnBack.set(book().id)
                                             mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
                                         }
                                     }
                                     reactive {
                                         if (hasMore() && (filteredBooks().isEmpty() ||
                                             (filteredBooks().isNotEmpty() && lastIndex() >= filteredBooks().lastIndex - 10))) {
                                             launch { loadNextPage() }
                                         }
                                     }

                                }
                            }
                        }
                    }
                )
            }

            shownWhen { initialLoad.state().ready && isLoading() && hasMore() }.centered.inglenookActivityIndicator()

            // Books grid/list
//            gridListView(
//                items = filteredBooks,
//                keySelector = { it.id },
//                gridItem = { book ->
//                    bookCard(book) {
//                        lastItemViewedScrollToOnBack.set(book().id)
//                        mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
//                    }
//                },
//                listItem = { book ->
//                    bookListItem(book) {
//                        lastItemViewedScrollToOnBack.set(book().id)
//                        mainPageNavigator.navigate(BookDetailPage(book.invoke().id))
//                    }
//                }
//            )
        }
    }
}
