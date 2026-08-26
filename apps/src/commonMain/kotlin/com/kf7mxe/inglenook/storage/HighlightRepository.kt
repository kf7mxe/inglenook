package com.kf7mxe.inglenook.storage

import com.kf7mxe.inglenook.Highlight
import com.kf7mxe.inglenook.jellyfin.serverScopedProperty
import com.lightningkite.kiteui.reactive.PersistentProperty
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Repository for managing ebook highlights and annotations
@OptIn(ExperimentalTime::class)
object HighlightRepository {
    // Stored highlights (persisted, scoped per server)
    private val storedHighlights: PersistentProperty<List<Highlight>>
        get() = serverScopedProperty("highlights", emptyList())

    fun getAllHighlights(): List<Highlight> {
        return storedHighlights.value.sortedByDescending { it.createdAt }
    }

    fun getHighlightsForBook(bookId: String): List<Highlight> {
        return storedHighlights.value
            .filter { it.bookId == bookId }
            .sortedBy { it.createdAt }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getHighlight(id: Uuid): Highlight? {
        return storedHighlights.value.find { it._id == id }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createHighlight(
        bookId: String,
        locator: String,
        color: String = "#FFFF00",
        note: String? = null,
        chapterName: String? = null
    ): Highlight {
        val highlight = Highlight(
            _id = Uuid.random(),
            bookId = bookId,
            locator = locator,
            color = color,
            note = note,
            chapterName = chapterName,
            createdAt = kotlin.time.Clock.System.now()
        )
        storedHighlights.value = storedHighlights.value + highlight
        return highlight
    }

    @OptIn(ExperimentalUuidApi::class)
    fun updateHighlight(highlight: Highlight) {
        storedHighlights.value = storedHighlights.value.map {
            if (it._id == highlight._id) highlight else it
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun deleteHighlight(id: Uuid) {
        storedHighlights.value = storedHighlights.value.filter { it._id != id }
    }

    fun deleteHighlightsForBook(bookId: String) {
        storedHighlights.value = storedHighlights.value.filter { it.bookId != bookId }
    }

    fun hasHighlights(bookId: String): Boolean {
        return storedHighlights.value.any { it.bookId == bookId }
    }
}
