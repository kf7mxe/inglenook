package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.mutableRememberSuspending
import kotlinx.coroutines.launch

fun ViewWriter.seriesDialog(
    book: Book,
    onApplied: () -> Unit,
    onDismiss: () -> Unit
) {
    val seriesName = Signal(book.seriesName ?: "")
    val seriesIndex = Signal(book.indexNumber ?: 1)
    val isApplying = Signal(false)
    val statusMessage = Signal<String?>(null)
    
    val allSeries = mutableRememberSuspending { 
        jellyfinClient.value?.getAllSeries() ?: emptyList() 
    }

    fun applySeries() {
        AppScope.launch {
            isApplying.value = true
            statusMessage.value = null
            try {
                val client = jellyfinClient.value
                val success = client?.setSeries(
                    itemId = book.id,
                    seriesName = seriesName.value,
                    seriesIndex = seriesIndex.value
                ) ?: false
                if (success) {
                    onApplied()
                } else {
                    statusMessage.value = "Failed to update series"
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
                expanding.h3 { content = if (book.seriesName != null) "Edit Series" else "Add to Series" }
                button {
                    icon(Icon.close, "Close")
                    onClick { onDismiss() }
                }
            }

            // Series Name Input
            field("Series Name") {
                textInput {
                    hint = "e.g. The Stormlight Archive"
                    content bind seriesName
                }
            }

            // Series Index Input
            field("Book Number in Series") {
                numberInput {
                    content bind seriesIndex.lens(
                        get = { it.toDouble() },
                        modify = { _, it -> it?.toInt() ?: 1 }
                    )
                }
            }

            // Status message
            shownWhen { statusMessage() != null }.subtext {
                ::content { statusMessage() ?: "" }
            }

            // Applying indicator
            shownWhen { isApplying() }.centered.text { content = "Updating series..." }

            important.button {
                text("Save")
                ::enabled { seriesName().isNotBlank() && !isApplying() }
                onClick { applySeries() }
            }

            separator()

            h4("Existing Series")
            
            shownWhen { allSeries.state().ready && allSeries().isEmpty() }.subtext("No existing series found")
            
            shownWhen { allSeries.state().ready && allSeries().isNotEmpty() }.col {
                forEach(allSeries) { series ->
                    button {
                        row {
                            expanding.text { content = series.name }
                            subtext { content = "${series.bookCount} books" }
                        }
                        onClick {
                            seriesName.value = series.name
                        }
                    }
                    separator()
                }
            }
        }
    }
}
