package com.kf7mxe.inglenook.storage

import com.kf7mxe.inglenook.Bookshelf
import com.lightningkite.kiteui.reactive.PersistentProperty
import kotlin.time.Clock
import kotlin.uuid.Uuid

// Repository for managing local bookshelves
object BookshelfRepository {
    // Stored bookshelves (persisted)
    private val storedBookshelves = PersistentProperty<List<Bookshelf>>("bookshelves", emptyList())

    fun getAllBookshelves(): List<Bookshelf> {
        return storedBookshelves.value.sortedBy { it.name.lowercase() }
    }

    fun getBookshelf(id: Uuid): Bookshelf? {
        return storedBookshelves.value.find { it._id == id }
    }

    fun createBookshelf(name: String): Bookshelf {
        val bookshelf = Bookshelf(
            _id = Uuid.random(),
            name = name,
            bookIds = emptyList(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        storedBookshelves.value = storedBookshelves.value + bookshelf
        return bookshelf
    }

    fun updateBookshelf(bookshelf: Bookshelf) {
        val updated = bookshelf.copy(updatedAt = Clock.System.now())
        storedBookshelves.value = storedBookshelves.value.map {
            if (it._id == bookshelf._id) updated else it
        }
    }

    fun deleteBookshelf(id: Uuid) {
        storedBookshelves.value = storedBookshelves.value.filter { it._id != id }
    }

    fun addBookToBookshelf(bookshelfId: Uuid, bookId: String) {
        val bookshelf = getBookshelf(bookshelfId) ?: return
        if (bookId !in bookshelf.bookIds) {
            val updated = bookshelf.copy(
                bookIds = bookshelf.bookIds + bookId,
                updatedAt = Clock.System.now()
            )
            storedBookshelves.value = storedBookshelves.value.map {
                if (it._id == bookshelfId) updated else it
            }
        }
    }

    fun removeBookFromBookshelf(bookshelfId: Uuid, bookId: String) {
        val bookshelf = getBookshelf(bookshelfId) ?: return
        val updated = bookshelf.copy(
            bookIds = bookshelf.bookIds - bookId,
            updatedAt = Clock.System.now()
        )
        storedBookshelves.value = storedBookshelves.value.map {
            if (it._id == bookshelfId) updated else it
        }
    }

    fun getBookshelvesContainingBook(bookId: String): List<Bookshelf> {
        return storedBookshelves.value.filter { bookId in it.bookIds }
    }
}
