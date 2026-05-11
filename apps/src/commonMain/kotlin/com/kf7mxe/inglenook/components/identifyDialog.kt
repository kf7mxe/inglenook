package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.RemoteSearchResultDto
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.launch

fun ViewWriter.identifyDialog(
    bookId: String,
    bookTitle: String,
    onApplied: () -> Unit,
    onDismiss: () -> Unit
) {
    val searchQuery = Signal(bookTitle)
    val searchResults = Signal<List<RemoteSearchResultDto>>(emptyList())
    val isSearching = Signal(false)
    val replaceExisting = Signal(false)
    val isApplying = Signal(false)
    val statusMessage = Signal<String?>(null)
    val warningMessage = Signal<String?>(null)

    fun doSearch() {
        AppScope.launch {
            val query = searchQuery.value
            if (query.isBlank()) return@launch
            isSearching.value = true
            statusMessage.value = null
            try {
                val client = jellyfinClient.value
                val searchResponse = client?.searchRemoteMetadata(
                    query = query,
                    itemId = bookId
                )
                searchResults.value = searchResponse?.Results ?: emptyList()
                warningMessage.value = searchResponse?.Warning
                if (searchResults.value.isEmpty() && warningMessage.value == null) {
                    statusMessage.value = "No results found"
                }
            } catch (e: Exception) {
                statusMessage.value = "Search failed: ${e.message}"
            } finally {
                isSearching.value = false
            }
        }
    }

    fun applyResult(result: RemoteSearchResultDto) {
        AppScope.launch {
            isApplying.value = true
            statusMessage.value = null
            try {
                val client = jellyfinClient.value
                val success = client?.applyRemoteMetadata(
                    itemId = bookId,
                    provider = result.Provider,
                    providerId = result.ProviderId,
                    replaceExisting = replaceExisting.value
                ) ?: false
                if (success) {
                    statusMessage.value = "Metadata applied successfully"
                    onApplied()
                } else {
                    statusMessage.value = "Failed to apply metadata"
                }
            } catch (e: Exception) {
                statusMessage.value = "Error: ${e.message}"
            } finally {
                isApplying.value = false
            }
        }
    }

    card.frame {
        padded.scrolling.col {
            // Header
            row {
                expanding.h3 { content = "Identify" }
                button {
                    icon(Icon.close, "Close")
                    onClick { onDismiss() }
                }
            }

            // Search input
            row {
                expanding.textInput {
                    hint = "Search by title, author, ISBN, ASIN..."
                    content bind searchQuery
                }
                button {
                    icon(Icon.search, "Search")
                    onClick { doSearch() }
                    themeChoice += ImportantSemantic
                }
            }

            // Replace existing toggle
            row {
                checkbox {
                    checked bind replaceExisting
                }
                expanding.text { content = "Replace existing metadata" }
            }

            // Status message
            shownWhen { statusMessage() != null }.subtext {
                ::content { statusMessage() ?: "" }
            }

            // Rate limit warning banner
            shownWhen { warningMessage() != null }.row {
                padding = 0.5.rem
                gap = 0.5.rem
                themeChoice += ThemeDerivation {
                    it.copy(
                        id = "search-warning",
                        background = Color.fromHexString("#FFA000").applyAlpha(0.15f),
                        foreground = Color.fromHexString("#E65100"),
                    ).withBack
                }
                icon(Icon.info, "Warning")
                expanding.text {
                    ::content { warningMessage() ?: "" }
                }
            }

            // Loading indicator
            shownWhen { isSearching() }.centered.text { content = "Searching..." }

            // Applying indicator
            shownWhen { isApplying() }.centered.text { content = "Applying metadata..." }

            separator()

            // Search results
            shownWhen { searchResults().isNotEmpty() && !isSearching() }.col {
                forEach(searchResults) { result ->
                    button {
                        card.row {
                            gap = 0.75.rem

                            // Cover image
                            sizeConstraints(width = 3.rem, height = 4.rem).frame {
                                if (result.ImageUrl != null) {
                                    themed(ImageSemantic).image {
                                        source = ImageRemote(result.ImageUrl)
                                        scaleType = ImageScaleType.Crop
                                    }
                                } else {
                                    centered.icon(Icon.book, "Cover")
                                }
                            }

                            // Text content
                            expanding.col {
                                // Title and provider badge
                                row {
                                    expanding.col {
                                        text {
                                            content = result.Title
                                        }
                                        subtext {
                                            content = result.Authors.joinToString(", ").ifEmpty { "Unknown Author" }
                                        }
                                    }
                                    subtext {
                                        content = result.Provider.replaceFirstChar { it.uppercase() }
                                    }
                                }

                                // Additional details
                                row {
                                    if (result.Year != null) {
                                        subtext { content = "${result.Year}" }
                                    }
                                    if (result.Narrators.isNotEmpty()) {
                                        subtext { content = "Narrated by: ${result.Narrators.joinToString(", ")}" }
                                    }
                                }

                                if (result.SeriesName != null) {
                                    subtext {
                                        content = buildString {
                                            append(result.SeriesName)
                                            if (result.SeriesPosition != null) append(" #${result.SeriesPosition}")
                                        }
                                    }
                                }

                                if (!result.Description.isNullOrBlank()) {
                                    subtext {
                                        content = result.Description.take(200) +
                                            if (result.Description.length > 200) "..." else ""
                                    }
                                }

                                // Provider IDs
                                row {
                                    if (result.Isbn != null) {
                                        subtext { content = "ISBN: ${result.Isbn}" }
                                    }
                                    if (result.Asin != null) {
                                        subtext { content = "ASIN: ${result.Asin}" }
                                    }
                                }
                            }
                        }
                        onClick {
                            applyResult(result)
                        }
                    }
                }
            }

            // Initial search hint
            shownWhen { searchResults().isEmpty() && !isSearching() && statusMessage() == null }.centered.col {
                subtext { content = "Search for your book to find metadata from Audnexus and Google Books" }
            }
        }
    }

    // Auto-search on open
    doSearch()
}
