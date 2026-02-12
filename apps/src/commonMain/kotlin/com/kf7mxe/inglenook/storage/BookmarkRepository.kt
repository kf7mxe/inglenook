package com.kf7mxe.inglenook.storage

import com.kf7mxe.inglenook.Bookmark
import com.kf7mxe.inglenook.jellyfin.serverScopedProperty
import com.lightningkite.kiteui.reactive.PersistentProperty
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Repository for managing audiobook bookmarks
object BookmarkRepository {
    // Stored bookmarks (persisted, scoped per server)
    private val storedBookmarks: PersistentProperty<List<Bookmark>>
        get() = serverScopedProperty("bookmarks", emptyList())

    @OptIn(ExperimentalTime::class)
    fun getAllBookmarks(): List<Bookmark> {
        return storedBookmarks.value.sortedByDescending { it.createdAt }
    }

    fun getBookmarksForBook(bookId: String): List<Bookmark> {
        return storedBookmarks.value
            .filter { it.bookId == bookId }
            .sortedBy { it.positionTicks }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getBookmark(id: Uuid): Bookmark? {
        return storedBookmarks.value.find { it._id == id }
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun createBookmark(
        bookId: String,
        positionTicks: Long,
        note: String? = null,
        chapterName: String? = null
    ): Bookmark {
        val bookmark = Bookmark(
            _id = Uuid.random(),
            bookId = bookId,
            positionTicks = positionTicks,
            note = note,
            chapterName = chapterName,
            createdAt = kotlin.time.Clock.System.now()
        )
        storedBookmarks.value = storedBookmarks.value + bookmark
        return bookmark
    }

    @OptIn(ExperimentalUuidApi::class)
    fun updateBookmark(bookmark: Bookmark) {
        storedBookmarks.value = storedBookmarks.value.map {
            if (it._id == bookmark._id) bookmark else it
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun deleteBookmark(id: Uuid) {
        storedBookmarks.value = storedBookmarks.value.filter { it._id != id }
    }

    fun deleteBookmarksForBook(bookId: String) {
        storedBookmarks.value = storedBookmarks.value.filter { it.bookId != bookId }
    }

    fun hasBookmarks(bookId: String): Boolean {
        return storedBookmarks.value.any { it.bookId == bookId }
    }
}
