package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.check
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.debounceWrite
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun ViewWriter.addBookDialog(
    bookshelfId: Uuid,
    currentBookIds: Reactive<Set<String>>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val searchQuery = Signal("")
    val searchResults = rememberSuspending {
        val query = searchQuery()
        if (query.isBlank()) {
            println("DEBUG AddBookDialog: query is blank")
            return@rememberSuspending emptyList<Book>()
        }
        
        println("DEBUG AddBookDialog: searching for '$query'")
        try {
            val client = jellyfinClient.value
            if (client != null) {
                val searchResults = client.search(query)
                val results = searchResults.books
                println("DEBUG AddBookDialog: search returned ${results.size} books and ${searchResults.authors.size} authors")
                return@rememberSuspending results
            } else {
                println("DEBUG AddBookDialog: jellyfinClient.value is NULL")
            }
        } catch (e: Exception) {
            println("DEBUG AddBookDialog: search exception: $e")
            e.printStackTrace()
            ConnectivityState.onNetworkError(e.message ?: "Search failed")
        }
        return@rememberSuspending emptyList()
    }

    card.frame {
        sizeConstraints(width = 30.rem, height = 40.rem).padded.col {
            // Header
            row {
                expanding.h3 { content = "Add Books to Bookshelf" }
                button {
                    icon(Icon.close, "Close")
                    onClick { onDismiss() }
                }
            }

            // Search
            row {
                centered.icon(Icon.search, "Search")
                expanding.fieldTheme.textInput {
                    hint = "Search books..."
                    content bind searchQuery.debounceWrite(500.milliseconds)
                }
                shownWhen { searchQuery().isNotBlank() }.button {
                    icon(Icon.close, "Clear")
                    onClick { searchQuery.value = "" }
                }
            }

            separator()

            // Results
            expanding.frame {
                // Loading indicator
                shownWhen { !searchResults.state().ready && searchQuery().isNotBlank() }.centered.inglenookActivityIndicator()

                shownWhen { searchResults.state().ready && searchResults().isEmpty() && searchQuery().isNotBlank() }.centered.text("No results found")
                shownWhen { searchResults.state().ready && searchResults().isEmpty() && searchQuery().isBlank() }.centered.text("Search for books to add")
                
                // Use expanding here to ensure it fills the frame and gets proper size
                shownWhen { searchResults.state().ready && searchResults().isNotEmpty() }.expanding.recyclerView {
                    children(searchResults, id = { it.id }) { book ->
                        val isAlreadyIn = remember { book().id in currentBookIds() }
                        
                        card.padded.row {
                            gap = 0.5.rem
                            
                            expanding.col {
                                gap = 0.rem
                                text { ::content { book().title } }
                                subtext { ::content { book().authors.joinToString { it.name } } }
                            }
                            
                            button {
                                icon {
                                    ::source { if (isAlreadyIn()) Icon.check else Icon.add }
                                    ::description { if (isAlreadyIn()) "Added" else "Add" }
                                }
                                onClick {
                                    if (!isAlreadyIn()) {
                                        AppScope.launch {
                                            BookshelfRepository.addBookToBookshelf(bookshelfId, book().id)
                                            onRefresh()
                                        }
                                    }
                                }
                                themeChoice += ImportantSemantic
                            }
                        }
                    }
                }
            }

            // Footer
            button {
                centered.text("Done")
                onClick { onDismiss() }
                themeChoice += ImportantSemantic
            }
        }
    }
}
