
package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.Author
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.ThemePreset
import com.kf7mxe.inglenook.components.coverImage
import com.kf7mxe.inglenook.components.emptyState
import com.kf7mxe.inglenook.components.gridListView
import com.kf7mxe.inglenook.components.viewModeToggleButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.currentThemePreset
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.lastItemViewedScrollToOnBack
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val AUTHOR_PAGE_SIZE = 50

@Serializable
enum class AuthorSortOption(val label: String, val sortBy: String, val sortOrder: String) {
    NameAsc("Name A-Z", "SortName", "Ascending"),
    NameDesc("Name Z-A", "SortName", "Descending"),
    RecentlyAdded("Recent", "DateLastContentAdded", "Descending"),
    MostPlayed("Most Played", "PlayCount", "Descending")
}

@Routable("AuthorsPage")
class AuthorsPage(val sortBy: Signal<AuthorSortOption> = Signal(AuthorSortOption.NameAsc),
                  val bookTypeFilter: Signal<ItemType?> = Signal(null)
    ) : Page {
    override val title: Reactive<String> = Constant("Authors")

    override fun ViewWriter.render() {
        val loadedAuthors = Signal<List<Author>>(emptyList())
        val nextStartIndex = Signal(0)
        val isLoadingMore = Signal(false)
        val hasMore = Signal(true)
        val initialAuthorsComplete = Signal(false)

        val initialAuthors: Reactive<List<Author>> = rememberSuspending {
            initialAuthorsComplete.value = false
            try {
                ConnectivityState.offlineMode()
                val client = jellyfinClient()
                val bookType = bookTypeFilter()
                val sort = sortBy()
                val page = if (bookType == null) {
                    client?.getAuthorsPage(startIndex = 0, limit = AUTHOR_PAGE_SIZE, sortBy = sort.sortBy, sortOrder = sort.sortOrder) ?: emptyList()
                } else {
                    val books = client?.getAllBooks() ?: emptyList()
                    books.filter { it.itemType == bookType }
                        .flatMap { it.authors }
                        .distinctBy { it.id }
                        .sortedBy { it.name.lowercase() }
                        .take(AUTHOR_PAGE_SIZE)
                }
                loadedAuthors.value = page
                nextStartIndex.value = page.size
                hasMore.value = page.size >= AUTHOR_PAGE_SIZE
                page
            } finally {
                initialAuthorsComplete.value = true
            }
        }

        val loadNextPage: suspend () -> Unit = suspend loadNextPage@{
            if (isLoadingMore() || !hasMore()) return@loadNextPage
            val client = jellyfinClient() ?: return@loadNextPage
            val bookType = bookTypeFilter()
            val sort = sortBy()
            isLoadingMore.value = true
            try {
                val page = if (bookType == null) {
                    client.getAuthorsPage(nextStartIndex(), AUTHOR_PAGE_SIZE, sortBy = sort.sortBy, sortOrder = sort.sortOrder)
                } else {
                    val books = client.getAllBooks()
                    books.filter { it.itemType == bookType }
                        .flatMap { it.authors }
                        .distinctBy { it.id }
                        .sortedBy { it.name.lowercase() }
                        .drop(nextStartIndex())
                        .take(AUTHOR_PAGE_SIZE)
                }
                val existingIds = loadedAuthors().asSequence().map { it.id }.toHashSet()
                loadedAuthors.value = loadedAuthors() + page.filter { it.id !in existingIds }
                nextStartIndex.value += page.size
                hasMore.value = page.size >= AUTHOR_PAGE_SIZE
            } finally {
                isLoadingMore.value = false
            }
        }

        col {
            // Type filters, sort dropdown, and view toggle
            row {
                card.button {
                    text("All")
                    onClick { bookTypeFilter.value = null }
                    dynamicTheme { if (bookTypeFilter() == null) ImportantSemantic else null }
                }
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
                separator()
                card.button {
                    row {
                        icon(Icon.sort, "Sort")
                        text { ::content { sortBy().label } }
                    }
                    onClick {
                        val options = AuthorSortOption.entries
                        val currentIndex = options.indexOf(sortBy())
                        sortBy.value = options[(currentIndex + 1) % options.size]
                    }
                }
                viewModeToggleButton()
            }
            sizeConstraints(height = 0.02.rem).frame() {
                ::shown {
                    currentThemePreset() == ThemePreset.NeumorphismLight || currentThemePreset() == ThemePreset.NeumorphismDark
                }
            }

            // Loading state
            shownWhen { !initialAuthors.state().ready }.centered.inglenookActivityIndicator()

            // Connection error state
            shownWhen { initialAuthors.state().ready && loadedAuthors().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Empty state
            shownWhen { initialAuthors.state().ready && loadedAuthors().isEmpty() && ConnectivityState.lastNetworkError() == null }.emptyState(
                icon = Icon.person,
                title = "No authors found",
                description = "Your audiobook library has no authors"
            )

            gridListView(
                items = loadedAuthors,
                keySelector = { it.id },
                gridItem = { author ->
                    authorCard(author) {
                        lastItemViewedScrollToOnBack.set(author().id)
                        mainPageNavigator.navigate(AuthorDetailPage(author().id))
                    }
                },
                listItem = { author ->
                    authorListItem(author) {
                        lastItemViewedScrollToOnBack.set(author().id)
                        mainPageNavigator.navigate(AuthorDetailPage(author().id))
                    }
                },
                onNearEnd = {
                    if (!isLoadingMore() && hasMore()) loadNextPage()
                },
                restoreReady = { initialAuthorsComplete() },
                restoreIsLoading = { isLoadingMore() },
                restoreHasMore = { hasMore() },
                restoreLoadedCount = { nextStartIndex() }
            )

            shownWhen { isLoadingMore() && hasMore() }.centered.inglenookActivityIndicator()
        }
    }
}

// Author card component
fun ViewWriter.authorCard(author: Reactive<Author>, onClick: suspend () -> Unit) {
    card.button {
        col {
            // Author image/avatar
            centered.coverImage(
                    imageId = { author().imageId },
                    itemId = { author().id },
                    fallbackIcon = Icon.person.copy(width = 3.rem, height = 3.rem),
                    imageHeight = 6.rem,
                    scaleType = ImageScaleType.Crop
                )

            // Name
            centered.text {
                ::content { author().name }
                ellipsis = true
                lineClamp = 2
            }
        }
        this.onClick { onClick() }
    }
}

// Author list item component
fun ViewWriter.authorListItem(author: Reactive<Author>, onClick: suspend () -> Unit) {
    card.button {
        row {
            // Thumbnail
            centered.coverImage(
                imageId = { author().imageId },
                itemId = { author().id },
                fallbackIcon = Icon.person.copy(width = 2.rem, height = 2.rem),
                imageHeight = 4.rem,
                scaleType = ImageScaleType.Crop
            )

            // Author info
            centered.expanding.col {
                text {
                    ::content { author().name }
                    ellipsis = true
                    lineClamp = 2
                }
                subtext {
                    ::shown { author().overview != null }
                    ::content { author().overview ?: "" }
                    ellipsis = true
                    lineClamp = 2

                }
            }

            centered.icon(Icon.chevronRight, "View")
        }
        this.onClick { onClick() }
    }
}
