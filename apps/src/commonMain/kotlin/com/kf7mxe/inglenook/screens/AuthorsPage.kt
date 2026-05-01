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
import com.kf7mxe.inglenook.components.EmptyState
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

@Routable("AuthorsPage")
class AuthorsPage(val searchQuery: Signal<String> = Signal(""),
                  val bookTypeFilter: Signal<ItemType?> = Signal(null)
    ) : Page {
    override val title: Reactive<String> = Constant("Authors")

    override fun ViewWriter.render() {
        val filteredAuthors: Reactive<List<Author>> = rememberSuspending {
            ConnectivityState.offlineMode()
            val books = jellyfinClient()?.getAllBooks() ?: emptyList()
            val query = searchQuery().lowercase().trim()
            val authorsWithBookType = books.filter {  bookTypeFilter()?.let{bookTypeFilter -> it.itemType == bookTypeFilter }?:true }.map {it.authors}.flatten().distinctBy { it.id }
            if (query.isEmpty()) return@rememberSuspending authorsWithBookType.sortedBy { it.name.lowercase() }
            println("DEBUG book type filter ${bookTypeFilter()}")
            return@rememberSuspending authorsWithBookType.filter { it.name.lowercase().contains(query.lowercase()) }.sortedBy { it.name.lowercase() }
        }

        col {
//            paddingByEdge = Edges(1.rem, 0.rem, 1.rem, 0.rem)

            // Search bar and view toggle
            row {
                expanding.fieldTheme.textInput {
                    hint = "Search authors..."
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind searchQuery
                }
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
                viewModeToggleButton()
            }
            sizeConstraints(height = 0.02.rem).frame() {
                ::shown {
                    currentThemePreset() == ThemePreset.NeumorphismLight || currentThemePreset() == ThemePreset.NeumorphismDark
                }
            }

            // Loading state
            shownWhen { !filteredAuthors.state().ready }.centered.inglenookActivityIndicator()

            // Connection error state
            shownWhen { filteredAuthors.state().ready && filteredAuthors().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Empty state
            shownWhen { filteredAuthors.state().ready && filteredAuthors().isEmpty() && ConnectivityState.lastNetworkError() == null }.EmptyState(
                icon = Icon.person,
                title = "No authors found",
                description = "Your audiobook library has no authors"
            )

            // No search results
            shownWhen { filteredAuthors.state().ready && filteredAuthors().isNotEmpty() && filteredAuthors().isEmpty() }.centered.col {
                icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                text("No results found")
                subtext { ::content { "No authors match \"${searchQuery()}\"" } }
            }

            gridListView(
                items = filteredAuthors,
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
                }
            )
        }
    }
}

// Author card component
fun ViewWriter.authorCard(author: Reactive<Author>, onClick: suspend () -> Unit) {
    card.button {
        col {
            // Author image/avatar
            launch {
                println("DEBUG author card author().image id ${author().imageId}")
                println("DEBUG author card author().id ${author().id}")
            }
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
