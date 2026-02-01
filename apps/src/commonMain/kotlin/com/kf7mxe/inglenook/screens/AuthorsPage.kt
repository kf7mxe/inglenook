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
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.components.BookCard
import com.kf7mxe.inglenook.components.BookListItem
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch

@Routable("AuthorsPage")
class AuthorsPage : Page {
    override val title: Reactive<String> = Constant("Authors")

    override fun ViewWriter.render() {
        val searchQuery = Signal("")
//        val errorMessage = Signal<String?>(null)

        // Load authors
        val authors = rememberSuspending {
            val client = jellyfinClient.value
            client?.getAuthors() ?: emptyList()
        }

        // Filter authors based on search query - use () for reactive access
        val filteredAuthors: Reactive<List<Author>> = remember {
            val query = searchQuery().lowercase().trim()
            if (query.isEmpty()) return@remember authors()
            return@remember authors().filter { author ->
                author.name.lowercase().contains(query)
            }
        }


        col {

            // Search bar and view toggle
            row {
                expanding.fieldTheme.textInput {
                    hint = "Search authors..."
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind searchQuery
                }

                // View mode toggle
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


            // Loading state
            shownWhen { !authors.state().ready }.centered.activityIndicator()

            // Error state
//                shownWhen { errorMessage() != null && !isLoading() }.centered.col {
//                    gap = 0.5.rem
//                    text { ::content { errorMessage() ?: "" } }
//                    button {
//                        text("Retry")
//                        onClick { loadAuthors() }
//                    }
//                }

            // Content
            // Empty state
            shownWhen { authors.state().ready && authors().isEmpty() }.centered.col {
                icon(Icon.person.copy(width = 3.rem, height = 3.rem), "Authors")
                text("No authors found")
                subtext("Your audiobook library has no authors")
            }

            // No search results
            shownWhen { authors.state().ready && authors().isNotEmpty() && filteredAuthors().isEmpty() }.centered.col {
                icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                text("No results found")
                subtext { ::content { "No authors match \"${searchQuery()}\"" } }
            }







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
                                    children(filteredAuthors, { it.id }) { author ->
                                        AuthorCard(author) {
                                            mainPageNavigator.navigate(AuthorDetailPage(author().id))
                                        }
                                    }

                                }
                            }

                            ViewMode.List -> {
                                expanding.recyclerView {
                                    children(filteredAuthors, { it.id }) { author ->
                                        AuthorListItem(author) {
                                            mainPageNavigator.navigate(AuthorDetailPage(author().id))
                                        }
                                    }
                                }
                            }
                        }
                    })

            }







            // List view
//                shownWhen { viewMode() == ViewMode.List }.col {
//                    gap = 0.5.rem
//                    forEach(filteredAuthors) { author ->
//                        AuthorListItem(author) {
//                            mainPageNavigator.navigate(AuthorDetailPage(author.id))
//                        }
//                    }
//                }
        }
    }
}

// Author card component
fun ViewWriter.AuthorCard(author: Reactive<Author>, onClick: suspend () -> Unit) {
    button {
        col {
            // Author image/avatar
            sizedBox(SizeConstraints(width = 6.rem, height = 6.rem)).frame {
                image {
                    this.rView::shown{
                        author().imageId != null
                    }
                    ::source {
                        val client = jellyfinClient()
                        if (client != null && author().imageId != null) {
                            ImageRemote(client.getImageUrl(author().imageId, author().id))
                        } else null
                    }
                    scaleType = ImageScaleType.Crop
                }
                centered.icon {
                    ::shown {
                        author().imageId == null
                    }
                    source = Icon.person.copy(width = 3.rem, height = 3.rem)
                }
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
            sizedBox(SizeConstraints(width = 4.rem, height = 4.rem)).frame {
                image {
                    rView::shown {
                        author().imageId != null
                    }
                    ::source {
                        val client = jellyfinClient()
                        if (client != null && author().imageId != null) {
                            ImageRemote(client.getImageUrl(author().imageId, author().id))
                        } else null
                    }
                    scaleType = ImageScaleType.Crop
                }
                centered.icon {
                    ::shown {
                        author().imageId == null
                    }
                    source = Icon.person.copy(width = 2.rem, height = 2.rem)
                }
            }

            // Author info
            expanding.col {
                text {
                    ::content { author().name }
                    ellipsis = true
                }
                subtext {
                    ::shown {
                        author().overview != null
                    }
                    ::content { author().overview ?: "" }
                    ellipsis = true
                }
            }

            centered.icon(Icon.chevronRight, "View")
        }
        this.onClick { onClick() }
    }
}
