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
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch

@Routable("AuthorsPage")
class AuthorsPage : Page {
    override val title: Reactive<String> = Constant("Authors")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val authors = Signal<List<Author>>(emptyList())
        val searchQuery = Signal("")
        val viewMode = Signal(ViewMode.Grid)
        val errorMessage = Signal<String?>(null)

        // Filter authors based on search query
        val filteredAuthors: Reactive<List<Author>> = remember {
            val query = searchQuery.value.lowercase().trim()
            if (query.isEmpty()) return@remember authors.value
            return@remember authors.value.filter { author ->
                author.name.lowercase().contains(query)
            }
        }

        // Load authors
        fun loadAuthors() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        authors.value = client.getAuthors()
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load authors: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadAuthors()

        col {
            gap = 0.rem

            // Search bar and view toggle
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.textField {
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

            separator()

            expanding.frame {
                // Loading state
                shownWhen { isLoading() }.centered.activityIndicator()

                // Error state
                shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                    gap = 0.5.rem
                    text { ::content { errorMessage() ?: "" } }
                    button {
                        text("Retry")
                        onClick { loadAuthors() }
                    }
                }

                // Content
                shownWhen { !isLoading() && errorMessage() == null }.frame {
                    // Empty state
                    shownWhen { authors().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        icon(Icon.person.copy(width = 3.rem, height = 3.rem), "Authors")
                        text("No authors found")
                        subtext("Your audiobook library has no authors")
                    }

                    // No search results
                    shownWhen { authors().isNotEmpty() && filteredAuthors().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        icon(Icon.search.copy(width = 3.rem, height = 3.rem), "Search")
                        text("No results found")
                        subtext { ::content { "No authors match \"${searchQuery()}\"" } }
                    }

                    // Authors list/grid
                    shownWhen { filteredAuthors().isNotEmpty() }.scrolls.col {
                        padding = 1.rem
                        gap = 1.rem

                        // Grid view
                        shownWhen { viewMode() == ViewMode.Grid }.row {
                            gap = 1.rem
                            forEach(filteredAuthors) { author ->
                                AuthorCard(author) {
                                    mainPageNavigator.navigate(AuthorDetailPage(author.id))
                                }
                            }
                        }

                        // List view
                        shownWhen { viewMode() == ViewMode.List }.col {
                            gap = 0.5.rem
                            forEach(filteredAuthors ) { author ->
                                AuthorListItem(author) {
                                    mainPageNavigator.navigate(AuthorDetailPage(author.id))
                                }
                                separator()
                            }
                        }
                    }
                }
            }
        }
    }
}

// Author card component
fun ViewWriter.AuthorCard(author: Author, onClick: () -> Unit) {
    button {
        col {
            gap = 0.5.rem

            // Author image/avatar
            sizedBox(SizeConstraints(width = 6.rem, height = 6.rem)).frame {
                if (author.imageId != null) {
                    val client = jellyfinClient.value
                    image {
                        source = ImageRemote(client?.getImageUrl(author.imageId) ?: "")
                        scaleType = ImageScaleType.Crop
                    }
                } else {
                    centered.icon(Icon.person.copy(width = 3.rem, height = 3.rem), author.name)
                }
            }

            // Name
            centered.text {
                content = author.name
                ellipsis = true
            }
        }
        this.onClick { onClick() }
    }
}

// Author list item component
fun ViewWriter.AuthorListItem(author: Author, onClick: () -> Unit) {
    button {
        row {
            gap = 1.rem
            padding = 0.5.rem

            // Thumbnail
            sizedBox(SizeConstraints(width = 4.rem, height = 4.rem)).frame {
                if (author.imageId != null) {
                    val client = jellyfinClient.value
                    image {
                        source = ImageRemote(client?.getImageUrl(author.imageId) ?: "")
                        scaleType = ImageScaleType.Crop
                    }
                } else {
                    centered.icon(Icon.person.copy(width = 2.rem, height = 2.rem), author.name)
                }
            }

            // Author info
            expanding.col {
                gap = 0.25.rem
                text {
                    content = author.name
                    ellipsis = true
                }
                if (author.overview != null) {
                    subtext {
                        content = author.overview ?: ""
                        ellipsis = true
                    }
                }
            }

            centered.icon(Icon.chevronRight, "View")
        }
        this.onClick { onClick() }
    }
}
