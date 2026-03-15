package com.kf7mxe.inglenook.demo

import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.BookshelfResponse
import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.kf7mxe.inglenook.jellyfin.PluginChapter
import com.kf7mxe.inglenook.jellyfin.SearchResults
import com.kf7mxe.inglenook.jellyfin.ServerInfoResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DemoJellyfinClient : JellyfinClient(
    serverUrl = "https://demo.inglenook.app",
    accessToken = "demo-token",
    userId = "demo-user",
) {
    override suspend fun getServerInfo(): ServerInfoResponse {
        return ServerInfoResponse(Id = "demo-server-id", ServerName = "Inglenook Demo")
    }

    override suspend fun pingServer(): Boolean = true

    override suspend fun getLibraries(): List<JellyfinLibrary> {
        return listOf(DemoData.library)
    }

    override suspend fun getAllBooks(libraryId: String?, forceRefresh: Boolean): List<Book> {
        return DemoData.allBooks
    }

    override suspend fun getInProgressBooks(): List<Book> {
        return DemoData.inProgressBooks
    }

    override suspend fun getRecentlyAddedBooks(): List<Book> {
        return DemoData.recentlyAddedBooks
    }

    override suspend fun getSuggestedBooks(): List<Book> {
        return DemoData.suggestedBooks
    }

    override suspend fun getBook(itemId: String): Book? {
        return DemoData.allBooks.find { it.id == itemId }
    }

    override suspend fun getAudiobookChapters(itemId: String): List<PluginChapter> {
        val book = DemoData.allBooks.find { it.id == itemId } ?: return emptyList()
        return book.chapters.map { PluginChapter(Name = it.name, StartPositionTicks = it.startPositionTicks) }
    }

    override suspend fun getAuthors(forceRefresh: Boolean): List<Author> {
        return DemoData.authors
    }

    override suspend fun getAuthor(authorId: String): Author? {
        return DemoData.authors.find { it.id == authorId }
    }

    override suspend fun getBooksByAuthor(authorId: String): List<Book> {
        return DemoData.allBooks.filter { book -> book.authors.any { it.id == authorId } }
    }

    override suspend fun getAllSeries(): List<Series> = emptyList()

    override suspend fun getBooksBySeries(seriesName: String): List<Book> = emptyList()

    override suspend fun getSeriesByAuthor(authorId: String): List<Series> = emptyList()

    override fun getImageUrl(imageId: String?, itemId: String?, imageType: String): String {
        val id = itemId ?: imageId ?: return ""
        return DemoData.coverUrls[id] ?: ""
    }

    override fun getAudioStreamUrl(itemId: String, startPositionTicks: Long, useHls: Boolean): String {
        return DemoData.audioUrls[itemId] ?: ""
    }

    override fun getDownloadUrl(book: Book): String {
        return DemoData.ebookUrls[book.id] ?: DemoData.audioUrls[book.id] ?: ""
    }

    override fun getEbookDownloadUrl(bookId: String): String {
        return DemoData.ebookUrls[bookId] ?: ""
    }

    override suspend fun search(query: String, limit: Int): SearchResults {
        val lowerQuery = query.lowercase()
        val matchingBooks = DemoData.allBooks.filter { book ->
            book.title.lowercase().contains(lowerQuery) ||
                book.authors.any { it.name.lowercase().contains(lowerQuery) }
        }
        val matchingAuthors = DemoData.authors.filter { it.name.lowercase().contains(lowerQuery) }
        return SearchResults(books = matchingBooks, authors = matchingAuthors)
    }

    override suspend fun reportPlaybackStart(itemId: String, positionTicks: Long) { /* no-op */ }
    override suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, isPaused: Boolean) { /* no-op */ }
    override suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long) { /* no-op */ }

    // In-memory bookshelf storage for demo mode
    private val demoBookshelves = mutableListOf<BookshelfResponse>()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getBookshelves(): List<BookshelfResponse> = demoBookshelves.toList()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createBookshelf(name: String): BookshelfResponse {
        val bookshelf = BookshelfResponse(
            Id = Uuid.random().toString(),
            Name = name,
            BookIds = emptyList()
        )
        demoBookshelves.add(bookshelf)
        return bookshelf
    }

    override suspend fun updateBookshelf(id: String, name: String?, bookIds: List<String>?, coverImageUrl: String?): BookshelfResponse? {
        val index = demoBookshelves.indexOfFirst { it.Id == id }
        if (index == -1) return null
        val existing = demoBookshelves[index]
        val updated = existing.copy(
            Name = name ?: existing.Name,
            BookIds = bookIds ?: existing.BookIds,
            CoverImageUrl = coverImageUrl ?: existing.CoverImageUrl
        )
        demoBookshelves[index] = updated
        return updated
    }

    override suspend fun deleteBookshelf(id: String): Boolean {
        val sizeBefore = demoBookshelves.size
        demoBookshelves.removeAll { it.Id == id }
        return demoBookshelves.size < sizeBefore
    }
}
