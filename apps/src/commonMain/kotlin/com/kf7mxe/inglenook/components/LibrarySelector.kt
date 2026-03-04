package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.JellyfinLibrary
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.selectedLibraryIds
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.rememberSuspending

fun ViewWriter.librarySelector() {
    val libraries = rememberSuspending {
        val client = jellyfinClient.value
        client?.getLibraries() ?: emptyList()
    }

    // Keep a cached copy of all library IDs for use in onClick (non-reactive context)
    val allLibraryIds = Signal<List<String>>(emptyList())

    card.col {
        shownWhen { !libraries.state().ready }.centered.row {
            inglenookActivityIndicator()
            text("Loading libraries...")
        }

        shownWhen {
            val ready = libraries.state().ready
            if (ready) {
                allLibraryIds.value = libraries().map { lib -> lib.id }
            }
            ready
        }.col {
            // Select All / Clear buttons
            row {
                button {
                    text("Select All")
                    onClick {
                        selectedLibraryIds.value = allLibraryIds.value
                    }
                }

                button {
                    text("Clear All")
                    onClick {
                        selectedLibraryIds.value = emptyList()
                    }
                }

                expanding.space(1.0)

                subtext {
                    ::content {
                        if (selectedLibraryIds().isEmpty()) "Showing all libraries" else ""
                    }
                }
            }

            separator()
            col {
                forEach(libraries) { library ->
                    button {
                        row {
                            checkbox {
                                checked bind selectedLibraryIds.lens(
                                    get = { it.contains(library.id) },
                                    modify = { list, isSelected ->
                                        if (isSelected) {
                                            list + library.id
                                        } else {
                                            list - library.id
                                        }
                                    }
                                )
                            }

                            expanding.col {
                                text(library.name)
                                shownWhen { library.collectionType != null }.subtext {
                                    content = library.collectionType ?: ""
                                }
                            }
                        }
                        onClick {
                            // Toggle selection
                            val currentIds = selectedLibraryIds.value
                            selectedLibraryIds.value = if (library.id in currentIds) {
                                currentIds - library.id
                            } else {
                                currentIds + library.id
                            }
                        }
                        dynamicTheme {
                            if (library.id in selectedLibraryIds()) SelectedSemantic else null
                        }
                    }
                    separator()
                }
            }
        }
    }
}
