package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class AuthorsPage(val unit: Unit = Unit) : Page {
    override val title: ReactiveContext.() -> String = { "Authors" }

    override fun ViewWriter.render() {
        val authors = Signal<List<Author>>(emptyList())
        val searchQuery = Signal("")
        val viewMode = Signal(ViewMode.Grid)
        val isLoading = Signal(true)

        // Load authors
        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    authors.value = client.getAuthors()
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

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

                button {
                    icon {
                        ::source { if (viewMode() == ViewMode.Grid) Icon.viewList else Icon.viewModule }
                    }
                    onClick {
                        viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                    }
                }
            }

            separator()

            // Content area
            expanding.col {
                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() }.expanding.col {
                    val filteredAuthors = remember {
                        val query = searchQuery().lowercase()
                        if (query.isBlank()) {
                            authors()
                        } else {
                            authors().filter { it.name.lowercase().contains(query) }
                        }
                    }

                    shownWhen { filteredAuthors().isEmpty() }.centered.col {
                        gap = 0.5.rem
                        text("No authors found")
                        shownWhen { searchQuery().isNotBlank() }.subtext("Try a different search term")
                    }

                    shownWhen { filteredAuthors().isNotEmpty() && viewMode() == ViewMode.Grid }.scrolls.col {
                        padding = 1.rem

                        reactiveSuspending {
                            clearChildren()
                            val authorsToShow = filteredAuthors()
                            val chunked = authorsToShow.chunked(4)

                            for (rowAuthors in chunked) {
                                row {
                                    gap = 1.rem
                                    for (author in rowAuthors) {
                                        expanding.button {
                                            col {
                                                gap = 0.5.rem
                                                // Author image or placeholder
                                                centered.sizedBox(SizeConstraints(width = 4.rem, height = 4.rem)) {
                                                    themeChoice = CircleSemantic.onNext
                                                    if (author.imageId != null) {
                                                        val client = jellyfinClient.value
                                                        image {
                                                            source = ImageRemote(client?.getImageUrl(author.imageId) ?: "")
                                                            scaleType = ImageScaleType.Crop
                                                        }
                                                    } else {
                                                        centered.icon(Icon.person, author.name)
                                                    }
                                                }
                                                centered.text {
                                                    content = author.name
                                                    align = Align.Center
                                                }
                                            }
                                            onClick {
                                                mainPageNavigator.navigate(AuthorDetailPage(author.id))
                                            }
                                        }
                                    }
                                    repeat(4 - rowAuthors.size) {
                                        expanding.space(1.0)
                                    }
                                }
                            }
                        }
                    }

                    shownWhen { filteredAuthors().isNotEmpty() && viewMode() == ViewMode.List }.scrolls.col {
                        reactiveSuspending {
                            clearChildren()
                            for (author in filteredAuthors()) {
                                button {
                                    row {
                                        gap = 1.rem

                                        // Author image or placeholder
                                        sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)) {
                                            themeChoice = CircleSemantic.onNext
                                            if (author.imageId != null) {
                                                val client = jellyfinClient.value
                                                image {
                                                    source = ImageRemote(client?.getImageUrl(author.imageId) ?: "")
                                                    scaleType = ImageScaleType.Crop
                                                }
                                            } else {
                                                centered.icon(Icon.person, author.name)
                                            }
                                        }

                                        expanding.col {
                                            gap = 0.rem
                                            text(author.name)
                                        }

                                        icon(Icon.chevronRight, "View")
                                    }
                                    onClick {
                                        mainPageNavigator.navigate(AuthorDetailPage(author.id))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
