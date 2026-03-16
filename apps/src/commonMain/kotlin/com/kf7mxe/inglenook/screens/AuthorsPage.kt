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
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.components.CoverImage
import com.kf7mxe.inglenook.components.EmptyState
import com.kf7mxe.inglenook.components.GridListView
import com.kf7mxe.inglenook.components.ViewModeToggleButton
import com.kf7mxe.inglenook.components.connectionError
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending

@Routable("AuthorsPage")
class AuthorsPage(val searchQuery: Signal<String> = Signal("")) : Page {
    override val title: Reactive<String> = Constant("Authors")

    override fun ViewWriter.render() {
        val authors = rememberSuspending {
            ConnectivityState.offlineMode()
            jellyfinClient()?.getAuthors() ?: emptyList()
        }

        val filteredAuthors: Reactive<List<Author>> = remember {
            val query = searchQuery().lowercase().trim()
            if (query.isEmpty()) return@remember authors()
            return@remember authors().filter { author ->
                author.name.lowercase().contains(query)
            }
        }

        col {
            paddingByEdge = Edges(1.rem, 0.rem, 1.rem, 0.rem)

            // Search bar and view toggle
            row {
                expanding.fieldTheme.textInput {
                    hint = "Search authors..."
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind searchQuery
                }
                ViewModeToggleButton()
            }

            // Loading state
            shownWhen { !authors.state().ready }.centered.inglenookActivityIndicator()

            // Connection error state
            shownWhen { authors.state().ready && authors().isEmpty() && ConnectivityState.lastNetworkError() != null }.connectionError {
                mainPageNavigator.navigate(LibraryPage())
            }

            // Empty state
            shownWhen { authors.state().ready && authors().isEmpty() && ConnectivityState.lastNetworkError() == null }.EmptyState(
                icon = Icon.person,
                title = "No authors found",
                description = "Your audiobook library has no authors"
            )

            // No search results
            shownWhen { authors.state().ready && authors().isNotEmpty() && filteredAuthors().isEmpty() }.centered.col {
                icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                text("No results found")
                subtext { ::content { "No authors match \"${searchQuery()}\"" } }
            }

            GridListView(
                items = filteredAuthors,
                keySelector = { it.id },
                gridItem = { author ->
                    AuthorCard(author) {
                        mainPageNavigator.navigate(AuthorDetailPage(author().id))
                    }
                },
                listItem = { author ->
                    AuthorListItem(author) {
                        mainPageNavigator.navigate(AuthorDetailPage(author().id))
                    }
                }
            )
        }
    }
}

// Author card component
fun ViewWriter.AuthorCard(author: Reactive<Author>, onClick: suspend () -> Unit) {
    button {
        col {
            // Author image/avatar
            frame {
                CoverImage(
                    imageId = { author().imageId },
                    itemId = { author().id },
                    fallbackIcon = Icon.person.copy(width = 3.rem, height = 3.rem),
                    imageHeight = 6.rem,
                    imageWidth = 6.rem,
                    scaleType = ImageScaleType.Crop
                )
            }

            // Name
            centered.text {
                ::content { author().name }
                ellipsis = true
            }
        }
        this.onClick { onClick() }
    }
}

// Author list item component
fun ViewWriter.AuthorListItem(author: Reactive<Author>, onClick: suspend () -> Unit) {
    button {
        row {
            // Thumbnail
            CoverImage(
                imageId = { author().imageId },
                itemId = { author().id },
                fallbackIcon = Icon.person.copy(width = 2.rem, height = 2.rem),
                imageHeight = 4.rem,
                imageWidth = 4.rem,
                scaleType = ImageScaleType.Crop
            )

            // Author info
            expanding.col {
                text {
                    ::content { author().name }
                    ellipsis = true
                }
                subtext {
                    ::shown { author().overview != null }
                    ::content { author().overview ?: "" }
                    ellipsis = true
                }
            }

            centered.icon(Icon.chevronRight, "View")
        }
        this.onClick { onClick() }
    }
}
