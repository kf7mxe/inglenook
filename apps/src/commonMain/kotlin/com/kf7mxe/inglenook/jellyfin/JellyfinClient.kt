package com.kf7mxe.inglenook.jellyfin

import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.AuthorInfo
import com.kf7mxe.inglenook.cache.ApiCache
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.downloads.toAudioBook
import com.kf7mxe.inglenook.util.CueParser
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class JellyfinClient @OptIn(ExperimentalUuidApi::class) constructor(
    val serverUrl: String,
    private var accessToken: String? = null,
    private var userId: String? = null,
    private val deviceId: String = Uuid.random().toString()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    private val deviceName = "Inglenook"
    private val clientVersion = "1.0.0"

    fun getAuthHeader(): String {
        val parts = mutableListOf(
            "MediaBrowser Client=\"$deviceName\"",
            "Device=\"$deviceName\"",
            "DeviceId=\"$deviceId\"",
            "Version=\"$clientVersion\""
        )
        accessToken?.let { parts.add("Token=\"$it\"") }
        return parts.joinToString(", ")
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun authenticate(username: String, password: String): JellyfinServerConfig {
        val response = client.post("$serverUrl/Users/AuthenticateByName") {
            contentType(ContentType.Application.Json)
            header("X-Emby-Authorization", getAuthHeader())
            setBody(AuthenticateRequest(username, password))
        }

        if (!response.status.isSuccess()) {
            throw Exception("Authentication failed: ${response.status}")
        }

        val authResponse: AuthenticateResponse = response.body()

        accessToken = authResponse.AccessToken
        userId = authResponse.User.Id

        // Get server info for name
        val serverInfo = getServerInfo()

        return JellyfinServerConfig(
            _id = Uuid.random(),
            serverUrl = serverUrl,
            userId = authResponse.User.Id,
            username = authResponse.User.Name,
            accessToken = authResponse.AccessToken,
            deviceId = deviceId,
            serverId = serverInfo?.Id,
            serverName = serverInfo?.ServerName
        )
    }

    private suspend fun getServerInfo(): ServerInfoResponse? {
        return try {
            val response = client.get("$serverUrl/System/Info/Public")
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pingServer(): Boolean {
        return try {
            val response = client.get("$serverUrl/System/Info/Public")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    private inline fun <T> handleNetworkException(e: Exception, fallback: T): T {
        reportNetworkError(e)
        return fallback
    }

    private fun reportNetworkError(e: Exception) {
        // Treat IO/network exceptions as connectivity issues.
        // Only exclude serialization and programming errors.
        val isNonNetworkError = e is kotlinx.serialization.SerializationException ||
                e is IllegalArgumentException ||
                e is IllegalStateException
        if (!isNonNetworkError) {
            ConnectivityState.onNetworkError(e.message ?: "Network error")
        }
    }

    suspend fun getLibraries(): List<JellyfinLibrary> {
        if (ConnectivityState.offlineMode.value) return emptyList()
        val uid = userId ?: return emptyList()

        return try {
            val response = client.get("$serverUrl/Users/$uid/Views") {
                header("X-Emby-Authorization", getAuthHeader())
            }

            if (!response.status.isSuccess()) return emptyList()

            val views: ViewsResponse = response.body()
            views.Items.map {
                JellyfinLibrary(
                    id = it.Id,
                    name = it.Name,
                    collectionType = it.CollectionType,
                    imageId = it.ImageTags?.Primary
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    suspend fun getAllBooks(libraryId: String? = null, forceRefresh: Boolean = false): List<Book> {
        val uid = userId ?: return emptyList()
        val libraryIds = if (libraryId != null) listOf(libraryId) else selectedLibraryIds.value
        val cacheKey = ApiCache.booksKey(libraryIds)

        // Check cache first (even in offline mode)
        if (ConnectivityState.offlineMode.value) {
            return ApiCache.get<List<Book>>(cacheKey)
                ?: ApiCache.getStale<List<Book>>(cacheKey)
                ?: emptyList()
        }

        return try {
            ApiCache.getOrPut(cacheKey, ApiCache.DEFAULT_TTL, forceRefresh, onError = ::reportNetworkError) {
                fetchAllBooks(uid, libraryIds)
            }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    private suspend fun fetchAllBooks(uid: String, libraryIds: List<String>): List<Book> {
        // If specific libraries are selected, query each one and merge results
        if (libraryIds.isNotEmpty()) {
            val allBooks = mutableListOf<Book>()
            var lastError: Exception? = null
            var anySucceeded = false
            for (libId in libraryIds) {
                val url = buildString {
                    append("$serverUrl/Users/$uid/Items")
                    append("?IncludeItemTypes=AudioBook,Book")
                    append("&Recursive=true")
                    append("&Fields=Chapters,Overview,People")
                    append("&SortBy=SortName")
                    append("&SortOrder=Ascending")
                    append("&ParentId=$libId")
                }

                try {
                    val response = client.get(url) {
                        header("X-Emby-Authorization", getAuthHeader())
                    }
                    if (response.status.isSuccess()) {
                        val itemsResponse: ItemsResponse = response.body()
                        allBooks.addAll(itemsResponse.Items.map { it.toAudioBook() })
                        anySucceeded = true
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            // If ALL libraries failed, propagate the error so cache fallback can kick in
            if (!anySucceeded && lastError != null) throw lastError
            return allBooks.distinctBy { it.id }.sortedBy { it.sortTitle ?: it.title }
        }

        // No specific libraries selected - get all books
        val url = buildString {
            append("$serverUrl/Users/$uid/Items")
            append("?IncludeItemTypes=AudioBook,Book")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }

        val response = client.get(url) {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val itemsResponse: ItemsResponse = response.body()
        return itemsResponse.Items.map { it.toAudioBook() }
    }

    suspend fun getInProgressBooks(): List<Book> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value
        val cacheKey = ApiCache.inProgressKey(libraryIds)

        // Check cache first (even in offline mode)
        if (ConnectivityState.offlineMode.value) {
            return ApiCache.get<List<Book>>(cacheKey)
                ?: ApiCache.getStale<List<Book>>(cacheKey)
                ?: emptyList()
        }

        return try {
            ApiCache.getOrPut(cacheKey, ApiCache.SHORT_TTL, onError = ::reportNetworkError) {
                if (libraryIds.isNotEmpty()) {
                    // Query each library separately for reliable filtering
                    val allInProgress = mutableListOf<Book>()
                    var lastError: Exception? = null
                    var anySucceeded = false
                    for (libId in libraryIds) {
                        try {
                            val url = buildString {
                                append("$serverUrl/Users/$uid/Items/Resume")
                                append("?IncludeItemTypes=AudioBook,Book")
                                append("&Recursive=true")
                                append("&Fields=Chapters,Overview,People")
                                append("&Limit=20")
                                append("&ParentId=$libId")
                            }
                            val response = client.get(url) {
                                header("X-Emby-Authorization", getAuthHeader())
                            }
                            if (response.status.isSuccess()) {
                                val itemsResponse: ItemsResponse = response.body()
                                allInProgress.addAll(itemsResponse.Items.map { it.toAudioBook() })
                                anySucceeded = true
                            }
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }
                    if (!anySucceeded && lastError != null) throw lastError
                    allInProgress.distinctBy { it.id }.take(10)
                } else {
                    val url = buildString {
                        append("$serverUrl/Users/$uid/Items/Resume")
                        append("?IncludeItemTypes=AudioBook,Book")
                        append("&Recursive=true")
                        append("&Fields=Chapters,Overview,People")
                        append("&Limit=50")
                    }
                    val response = client.get(url) {
                        header("X-Emby-Authorization", getAuthHeader())
                    }
                    if (!response.status.isSuccess()) throw Exception("Resume request failed: ${response.status}")
                    val itemsResponse: ItemsResponse = response.body()
                    itemsResponse.Items.map { it.toAudioBook() }.take(10)
                }
            }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    suspend fun getRecentlyAddedBooks(): List<Book> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value
        val cacheKey = ApiCache.recentKey(libraryIds)

        // Check cache first (even in offline mode)
        if (ConnectivityState.offlineMode.value) {
            return ApiCache.get<List<Book>>(cacheKey)
                ?: ApiCache.getStale<List<Book>>(cacheKey)
                ?: emptyList()
        }

        return try {
            ApiCache.getOrPut(cacheKey, ApiCache.SHORT_TTL, onError = ::reportNetworkError) {
            // If specific libraries are selected, query each and merge
            if (libraryIds.isNotEmpty()) {
                val allBooks = mutableListOf<Book>()
                var lastError: Exception? = null
                var anySucceeded = false
                for (libId in libraryIds) {
                    val url = buildString {
                        append("$serverUrl/Users/$uid/Items/Latest")
                        append("?IncludeItemTypes=AudioBook,Book")
                        append("&Fields=Chapters,Overview,People")
                        append("&Limit=20")
                        append("&ParentId=$libId")
                    }

                    try {
                        val response = client.get(url) {
                            header("X-Emby-Authorization", getAuthHeader())
                        }
                        if (response.status.isSuccess()) {
                            val items: List<JellyfinItem> = response.body()
                            allBooks.addAll(items.map { it.toAudioBook() })
                            anySucceeded = true
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                if (!anySucceeded && lastError != null) throw lastError
                allBooks.distinctBy { it.id }.take(20)
            } else {
                // No specific libraries - get all recent
                val url = buildString {
                    append("$serverUrl/Users/$uid/Items/Latest")
                    append("?IncludeItemTypes=AudioBook,Book")
                    append("&Fields=Chapters,Overview,People")
                    append("&Limit=20")
                }

                val response = client.get(url) {
                    header("X-Emby-Authorization", getAuthHeader())
                }

                if (!response.status.isSuccess()) throw Exception("Latest items request failed: ${response.status}")

                val items: List<JellyfinItem> = response.body()
                items.map { it.toAudioBook() }
            }
            }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    suspend fun getSuggestedBooks(): List<Book> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value
        val cacheKey = ApiCache.suggestedKey(libraryIds)

        // Check cache first (even in offline mode)
        if (ConnectivityState.offlineMode.value) {
            return ApiCache.get<List<Book>>(cacheKey)
                ?: ApiCache.getStale<List<Book>>(cacheKey)
                ?: emptyList()
        }

        return try {
            ApiCache.getOrPut(cacheKey, ApiCache.DEFAULT_TTL, onError = ::reportNetworkError) {
                // Suggestions endpoint doesn't support ParentId, so fetch all and cross-reference
                val url = buildString {
                    append("$serverUrl/Users/$uid/Suggestions")
                    append("?IncludeItemTypes=AudioBook,Book")
                    append("&Fields=Chapters,Overview,People")
                    append("&Limit=50")
                }

                val response = client.get(url) {
                    header("X-Emby-Authorization", getAuthHeader())
                }

                if (!response.status.isSuccess()) throw Exception("Suggestions request failed: ${response.status}")

                val itemsResponse: ItemsResponse = response.body()
                val allSuggestions = itemsResponse.Items.map { it.toAudioBook() }

                if (libraryIds.isNotEmpty()) {
                    // Try to cross-reference with cached book list to filter to selected libraries
                    val cachedBooks = ApiCache.get<List<Book>>(ApiCache.booksKey(libraryIds))
                    if (cachedBooks != null && cachedBooks.isNotEmpty()) {
                        val libraryBookIds = cachedBooks.map { it.id }.toSet()
                        allSuggestions
                            .filter { it.id in libraryBookIds }
                            .distinctBy { it.id }
                            .take(10)
                    } else {
                        // No cached book list — show all suggestions rather than nothing
                        allSuggestions.filter { it.id in libraryIds }.distinctBy { it.id }.take(10)
                    }
                } else {
                    allSuggestions.take(10)
                }
            }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    suspend fun getBook(itemId: String): Book? {
        val cacheKey = ApiCache.bookKey(itemId)

        if (ConnectivityState.offlineMode.value) {
            // Try cache first, then fall back to download metadata
            val cached = ApiCache.get<Book>(cacheKey)
                ?: ApiCache.getStale<Book>(cacheKey)
            if (cached != null) {
                val localPosition = com.kf7mxe.inglenook.playback.PlaybackState.getLocalPosition(itemId)
                return if (localPosition > 0L) {
                    cached.copy(userData = UserData(playbackPositionTicks = localPosition))
                } else cached
            }
            val download = com.kf7mxe.inglenook.downloads.DownloadManager.getDownload(itemId)
                ?: return null
            val localPosition = com.kf7mxe.inglenook.playback.PlaybackState.getLocalPosition(itemId)
            val book = download.toAudioBook()
            return if (localPosition > 0L) {
                book.copy(userData = UserData(playbackPositionTicks = localPosition))
            } else book
        }
        val uid = userId ?: return null

        return try {
            val response = client.get("$serverUrl/Users/$uid/Items/$itemId") {
                header("X-Emby-Authorization", getAuthHeader())
                parameter("Fields", "Chapters,Overview,People,Path,MediaSources")
            }

            if (!response.status.isSuccess()) return null

            val item: JellyfinItem = response.body()
            var book = item.toAudioBook()

            // Fetch chapters from the AudiobookChapters plugin endpoint
            val pluginChapters = getAudiobookChapters(itemId)
            if (pluginChapters.isNotEmpty()) {
                book = book.copy(
                    chapters = pluginChapters.map {
                        Chapter(
                            name = it.Name,
                            startPositionTicks = it.StartPositionTicks,
                            imageId = null
                        )
                    }
                )
            }

            // Cache the book for offline access
            ApiCache.put(cacheKey, book, ApiCache.DEFAULT_TTL)
            book
        } catch (e: Exception) {
            // Try cache/download fallback before reporting error
            val cached = ApiCache.get<Book>(cacheKey)
                ?: ApiCache.getStale<Book>(cacheKey)
            if (cached != null) {
                reportNetworkError(e)
                return cached
            }
            val download = com.kf7mxe.inglenook.downloads.DownloadManager.getDownload(itemId)
            if (download != null) {
                reportNetworkError(e)
                return download.toAudioBook()
            }
            handleNetworkException(e, null)
        }
    }

    /**
     * Fetch chapters from the AudiobookChapters plugin endpoint.
     * This plugin exposes chapters for audiobooks that Jellyfin's standard API doesn't return.
     */
    suspend fun getAudiobookChapters(itemId: String): List<PluginChapter> {
        return try {
            val response = client.get("$serverUrl/Inglenook/$itemId") {
                header("X-Emby-Token", accessToken ?: "")
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            // Plugin endpoint may not be installed — fall back to empty chapters
            emptyList()
        }
    }

    /**
     * Try to find and parse a .cue file for chapter information.
     * Uses the Jellyfin API to:
     * 1. List all items in the parent folder
     * 2. Find the .cue file
     * 3. Download and parse it
     */
    private suspend fun tryParseCueFile(userId: String, parentId: String, itemPath: String?): List<Chapter> {
        if (itemPath == null) return emptyList()

        return try {
            val folderPath = itemPath.substringBeforeLast("/")

            val dirResponse = client.get("$serverUrl/Environment/DirectoryContents") {
                header("X-Emby-Authorization", getAuthHeader())
                parameter("Path", folderPath)
            }

            if (!dirResponse.status.isSuccess()) return emptyList()

            val entries: List<FileSystemEntry> = dirResponse.body()

            val cueEntry = entries.firstOrNull {
                !it.IsDirectory && it.Name.lowercase().endsWith(".cue")
            } ?: return emptyList()

            val cueContent = client.get("$serverUrl/Environment/DownloadFile") {
                header("X-Emby-Authorization", getAuthHeader())
                parameter("Path", cueEntry.Path)
            }.bodyAsText()

            CueParser.parse(cueContent)

        } catch (e: Exception) {
            emptyList()
        }
    }

@Serializable
    data class FileSystemEntry(
        val Name: String,
        val Path: String,
        val IsDirectory: Boolean
    )


    /**
     * Download the raw content of a .cue file from Jellyfin.
     */
    suspend fun downloadCueFile(id: String): String? {
        val response = client.get("$serverUrl/Items/$id/Download") {
            header("X-Emby-Authorization", getAuthHeader())
        }
        if (!response.status.isSuccess()) return null
        return response.bodyAsText()
    }


    suspend fun getAuthors(forceRefresh: Boolean = false): List<Author> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value
        val cacheKey = ApiCache.authorsKey(libraryIds)

        if (ConnectivityState.offlineMode.value) {
            return ApiCache.get<List<Author>>(cacheKey)
                ?: ApiCache.getStale<List<Author>>(cacheKey)
                ?: emptyList()
        }

        return try {
            ApiCache.getOrPut(cacheKey, ApiCache.DEFAULT_TTL, forceRefresh, onError = ::reportNetworkError) {
                fetchAuthors(uid, libraryIds)
            }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    private suspend fun fetchAuthors(uid: String, libraryIds: List<String>): List<Author> {
        val authorsById = mutableMapOf<String, Author>()
        val authorTypes = setOf("Author", "AlbumArtist", "Artist", "Writer")

        // 1. Fetch AlbumArtists for rich metadata (images, overviews)
        val albumArtistUrls = if (libraryIds.isNotEmpty()) {
            libraryIds.map { libId ->
                buildString {
                    append("$serverUrl/Artists/AlbumArtists")
                    append("?UserId=$uid")
                    append("&SortBy=SortName")
                    append("&SortOrder=Ascending")
                    append("&ParentId=$libId")
                }
            }
        } else {
            listOf(buildString {
                append("$serverUrl/Artists/AlbumArtists")
                append("?UserId=$uid")
                append("&SortBy=SortName")
                append("&SortOrder=Ascending")
            })
        }

        for (url in albumArtistUrls) {
            try {
                val response = client.get(url) {
                    header("X-Emby-Authorization", getAuthHeader())
                }
                if (response.status.isSuccess()) {
                    val itemsResponse: ItemsResponse = response.body()
                    for (item in itemsResponse.Items) {
                        authorsById[item.Id] = Author(
                            id = item.Id,
                            name = item.Name,
                            imageId = item.ImageTags?.Primary,
                            overview = item.Overview
                        )
                    }
                }
            } catch (e: Exception) {
                // Continue with other sources
            }
        }

        // 2. Fetch all book items (AudioBook+Book) and extract authors from People metadata.
        //    This catches ebook authors that don't appear in AlbumArtists.
        val itemUrls = if (libraryIds.isNotEmpty()) {
            libraryIds.map { libId ->
                buildString {
                    append("$serverUrl/Users/$uid/Items")
                    append("?IncludeItemTypes=AudioBook,Book")
                    append("&Recursive=true")
                    append("&Fields=People")
                    append("&ParentId=$libId")
                }
            }
        } else {
            listOf(buildString {
                append("$serverUrl/Users/$uid/Items")
                append("?IncludeItemTypes=AudioBook,Book")
                append("&Recursive=true")
                append("&Fields=People")
            })
        }

        for (url in itemUrls) {
            try {
                val response = client.get(url) {
                    header("X-Emby-Authorization", getAuthHeader())
                }
                if (response.status.isSuccess()) {
                    val itemsResponse: ItemsResponse = response.body()
                    for (item in itemsResponse.Items) {
                        val people = item.People ?: continue
                        for (person in people.filter { it.Type in authorTypes }) {
                            val personId = person.Id ?: continue
                            if (personId !in authorsById) {
                                authorsById[personId] = Author(
                                    id = personId,
                                    name = normalizeAuthorName(person.Name),
                                    imageId = null,
                                    overview = null
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue with other libraries
            }
        }

        // Merge authors with the same normalized name (e.g. audiobook "Brandon Sanderson"
        // and ebook "Sanderson, Brandon" become one entry with both IDs).
        val mergedByName = mutableMapOf<String, Author>()
        for (author in authorsById.values) {
            val normalizedName = normalizeAuthorName(author.name)
            val existing = mergedByName[normalizedName.lowercase()]
            if (existing == null) {
                mergedByName[normalizedName.lowercase()] = author.copy(name = normalizedName)
            } else {
                // Combine IDs (comma-separated) so both ArtistIds and PersonIds queries work.
                // Prefer the entry that has richer metadata (image/overview).
                val combinedIds = (existing.id.split(",") + author.id.split(",")).distinct().joinToString(",")
                mergedByName[normalizedName.lowercase()] = if (existing.imageId != null) {
                    existing.copy(id = combinedIds)
                } else {
                    author.copy(id = combinedIds, name = normalizedName)
                }
            }
        }

        return mergedByName.values.sortedBy { it.name }
    }

    suspend fun getAuthor(authorId: String): Author? {
        if (ConnectivityState.offlineMode.value) return null
        val uid = userId ?: return null

        return try {
            // authorId may contain comma-separated IDs from merged authors; use the first one
            val primaryId = authorId.split(",").first()
            val response = client.get("$serverUrl/Users/$uid/Items/$primaryId") {
                header("X-Emby-Authorization", getAuthHeader())
            }

            if (!response.status.isSuccess()) return null

            val item: JellyfinItem = response.body()
            Author(
                id = authorId,
                name = normalizeAuthorName(item.Name),
                imageId = item.ImageTags?.Primary,
                overview = item.Overview
            )
        } catch (e: Exception) {
            handleNetworkException(e, null)
        }
    }

    suspend fun getBooksByAuthor(authorId: String): List<Book> {
        if (ConnectivityState.offlineMode.value) return emptyList()
        val uid = userId ?: return emptyList()

        // ArtistIds matches audiobooks (music model), PersonIds matches ebooks (book model)
        val audioBookUrl = buildString {
            append("$serverUrl/Users/$uid/Items")
            append("?IncludeItemTypes=AudioBook")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&ArtistIds=$authorId")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }

        val ebookUrl = buildString {
            append("$serverUrl/Users/$uid/Items")
            append("?IncludeItemTypes=Book")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&PersonIds=$authorId")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }

        return try {
            val results = mutableMapOf<String, Book>()

            val audioBookResponse = client.get(audioBookUrl) {
                header("X-Emby-Authorization", getAuthHeader())
            }
            if (audioBookResponse.status.isSuccess()) {
                val itemsResponse: ItemsResponse = audioBookResponse.body()
                itemsResponse.Items.forEach { results[it.Id] = it.toAudioBook() }
            }

            val ebookResponse = client.get(ebookUrl) {
                header("X-Emby-Authorization", getAuthHeader())
            }
            if (ebookResponse.status.isSuccess()) {
                val itemsResponse: ItemsResponse = ebookResponse.body()
                itemsResponse.Items.forEach { results[it.Id] = it.toAudioBook() }
            }

            results.values.sortedBy { it.sortTitle ?: it.title }
        } catch (e: Exception) {
            handleNetworkException(e, emptyList())
        }
    }

    fun getImageUrl(imageId: String?, itemId: String? = null, imageType: String = "Primary"): String {
        val id = itemId ?: imageId ?: return ""
        return "$serverUrl/Items/$id/Images/$imageType"
    }

    /**
     * Get all series from audiobooks (aggregated from books with seriesName).
     */
    suspend fun getAllSeries(): List<Series> {
        val books = getAllBooks()
        return books
            .filter { it.seriesName != null }
            .groupBy { it.seriesName!! }
            .map { (seriesName, seriesBooks) ->
                // Use the first book with a cover as the series cover
                val coverBook = seriesBooks.firstOrNull { it.coverImageId != null }
                Series(
                    id = seriesBooks.first().seriesId ?: seriesName, // Use seriesId if available, else name
                    name = seriesName,
                    imageId = coverBook?.coverImageId,
                    bookCount = seriesBooks.size,
                    overview = null
                )
            }
            .sortedBy { it.name }
    }

    /**
     * Get all books in a series by series name.
     */
    suspend fun getBooksBySeries(seriesName: String): List<Book> {
        val books = getAllBooks()
        return books
            .filter { it.seriesName == seriesName }
            .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
    }

    /**
     * Get series by a specific author.
     */
    suspend fun getSeriesByAuthor(authorId: String): List<Series> {
        val books = getBooksByAuthor(authorId)
        return books
            .filter { it.seriesName != null }
            .groupBy { it.seriesName!! }
            .map { (seriesName, seriesBooks) ->
                val coverBook = seriesBooks.firstOrNull { it.coverImageId != null }
                Series(
                    id = seriesBooks.first().seriesId ?: seriesName,
                    name = seriesName,
                    imageId = coverBook?.coverImageId,
                    bookCount = seriesBooks.size,
                    overview = null
                )
            }
            .sortedBy { it.name }
    }

    fun getAudioStreamUrl(
        itemId: String,
        startPositionTicks: Long = 0L,
        useHls: Boolean = false,
    ): String {
        return buildString {
            append("$serverUrl/Audio/$itemId/universal")
            append("?UserId=$userId")
            append("&DeviceId=$deviceId")
            append("&api_key=$accessToken")
            append("&Container=opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg")
            append("&TranscodingContainer=ts")
            if (useHls) {
                append("&TranscodingProtocol=hls")
            }
            append("&AudioCodec=aac")
            append("&EnableRedirection=true")
            append("&EnableRemoteMedia=false")
            if (startPositionTicks > 0) {
                append("&StartTimeTicks=$startPositionTicks")
            }
        }
    }

    /**
     * Get the download URL for a book. Uses audio stream for audiobooks, direct download for ebooks.
     */
    fun getDownloadUrl(book: com.kf7mxe.inglenook.Book): String {
        return if (book.itemType == com.kf7mxe.inglenook.ItemType.Ebook) {
            "$serverUrl/Items/${book.id}/Download?api_key=$accessToken"
        } else {
            getAudioStreamUrl(book.id)
        }
    }

    suspend fun search(query: String, limit: Int = 20): SearchResults {
        if (ConnectivityState.offlineMode.value) return SearchResults()
        val uid = userId ?: return SearchResults()
        val libraryIds = selectedLibraryIds.value

        // Search for audiobooks and ebooks
        val books = if (libraryIds.isNotEmpty()) {
            // Search within each selected library using ParentId
            val allBooks = mutableListOf<Book>()
            for (libId in libraryIds) {
                val url = buildString {
                    append("$serverUrl/Users/$uid/Items")
                    append("?SearchTerm=$query")
                    append("&IncludeItemTypes=AudioBook,Book")
                    append("&Recursive=true")
                    append("&Fields=Chapters,Overview,People")
                    append("&Limit=$limit")
                    append("&ParentId=$libId")
                }
                try {
                    val response = client.get(url) {
                        header("X-Emby-Authorization", getAuthHeader())
                    }
                    if (response.status.isSuccess()) {
                        val itemsResponse: ItemsResponse = response.body()
                        allBooks.addAll(itemsResponse.Items.map { it.toAudioBook() })
                    }
                } catch (e: Exception) {
                    // Continue with other libraries
                }
            }
            allBooks.distinctBy { it.id }.take(limit)
        } else {
            // No libraries selected - search globally
            val url = buildString {
                append("$serverUrl/Users/$uid/Items")
                append("?SearchTerm=$query")
                append("&IncludeItemTypes=AudioBook,Book")
                append("&Recursive=true")
                append("&Fields=Chapters,Overview,People")
                append("&Limit=$limit")
            }
            try {
                val response = client.get(url) {
                    header("X-Emby-Authorization", getAuthHeader())
                }
                if (response.status.isSuccess()) {
                    val itemsResponse: ItemsResponse = response.body()
                    itemsResponse.Items.map { it.toAudioBook() }
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Search for authors/people
        val authorsUrl = buildString {
            append("$serverUrl/Persons")
            append("?SearchTerm=$query")
            append("&UserId=$uid")
            append("&Limit=$limit")
        }

        val authors = try {
            val response = client.get(authorsUrl) {
                header("X-Emby-Authorization", getAuthHeader())
            }
            if (response.status.isSuccess()) {
                val itemsResponse: ItemsResponse = response.body()
                itemsResponse.Items.map {
                    Author(
                        id = it.Id,
                        name = it.Name,
                        imageId = it.ImageTags?.Primary,
                        overview = it.Overview
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return SearchResults(books = books, authors = authors)
    }

    suspend fun reportPlaybackStart(itemId: String, positionTicks: Long) {
        try {
            client.post("$serverUrl/Sessions/Playing") {
                header("X-Emby-Authorization", getAuthHeader())
                contentType(ContentType.Application.Json)
                setBody(PlaybackStartInfo(itemId, positionTicks))
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, isPaused: Boolean) {
        try {
            client.post("$serverUrl/Sessions/Playing/Progress") {
                header("X-Emby-Authorization", getAuthHeader())
                contentType(ContentType.Application.Json)
                setBody(PlaybackProgressInfo(itemId, positionTicks, isPaused))
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long) {
        try {
            client.post("$serverUrl/Sessions/Playing/Stopped") {
                header("X-Emby-Authorization", getAuthHeader())
                contentType(ContentType.Application.Json)
                setBody(PlaybackStopInfo(itemId, positionTicks))
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    // Quick Connect methods

    /**
     * Initiates a Quick Connect session.
     * Returns a Secret (for API calls) and Code (6-digit code to display to user).
     */
    suspend fun initiateQuickConnect(): QuickConnectResult {
        val response = client.post("$serverUrl/QuickConnect/Initiate") {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) {
            throw Exception("Failed to initiate Quick Connect: ${response.status}")
        }

        return response.body()
    }

    /**
     * Checks the status of a Quick Connect request.
     * Returns true if the user has authorized the request on the server.
     */
    suspend fun checkQuickConnectStatus(secret: String): Boolean {
        val response = client.get("$serverUrl/QuickConnect/Connect") {
            header("X-Emby-Authorization", getAuthHeader())
            parameter("Secret", secret)
        }

        if (!response.status.isSuccess()) {
            return false
        }

        val result: QuickConnectResult = response.body()
        return result.Authenticated == true
    }

    /**
     * Authenticates using a Quick Connect secret after the user has authorized it.
     * Returns a JellyfinServerConfig on success.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun authenticateWithQuickConnect(secret: String): JellyfinServerConfig {
        val response = client.post("$serverUrl/Users/AuthenticateWithQuickConnect") {
            header("X-Emby-Authorization", getAuthHeader())
            contentType(ContentType.Application.Json)
            setBody(QuickConnectAuthRequest(secret))
        }

        if (!response.status.isSuccess()) {
            throw Exception("Quick Connect authentication failed: ${response.status}")
        }

        val authResponse: AuthenticateResponse = response.body()

        accessToken = authResponse.AccessToken
        userId = authResponse.User.Id

        // Get server info for name
        val serverInfo = getServerInfo()

        return JellyfinServerConfig(
            _id = Uuid.random(),
            serverUrl = serverUrl,
            userId = authResponse.User.Id,
            username = authResponse.User.Name,
            accessToken = authResponse.AccessToken,
            deviceId = deviceId,
            serverId = serverInfo?.Id,
            serverName = serverInfo?.ServerName
        )
    }

    /**
     * Checks if Quick Connect is enabled on the server.
     */
    suspend fun isQuickConnectEnabled(): Boolean {
        return try {
            val response = client.get("$serverUrl/QuickConnect/Enabled") {
                header("X-Emby-Authorization", getAuthHeader())
            }
            if (response.status.isSuccess()) {
                // Response is just "true" or "false" as plain text
                response.bodyAsText().trim().lowercase() == "true"
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun JellyfinItem.toAudioBook(): Book {
        // Jellyfin uses various types for audiobook authors: Author, AlbumArtist, Artist, Writer
        val authorTypes = setOf("Author", "AlbumArtist", "Artist", "Writer")
        val authorPeople = People?.filter { it.Type in authorTypes } ?: emptyList()
        val narratorPerson = People?.find { it.Type == "Narrator" }

        // Build author list from multiple sources (People array, AlbumArtists, ArtistItems, etc.)
        val authorInfos = when {
            authorPeople.isNotEmpty() -> authorPeople.map { AuthorInfo(name = normalizeAuthorName(it.Name), id = it.Id) }
            AlbumArtists?.isNotEmpty() == true -> AlbumArtists.map { AuthorInfo(name = it.Name, id = it.Id) }
            ArtistItems?.isNotEmpty() == true -> ArtistItems.map { AuthorInfo(name = it.Name, id = it.Id) }
            AlbumArtist != null -> listOf(AuthorInfo(name = AlbumArtist, id = null))
            Artists?.isNotEmpty() == true -> Artists.map { AuthorInfo(name = it, id = null) }
            else -> emptyList()
        }
        val authorNames = authorInfos.mapNotNull { authorInfo -> authorInfo.id?.let { Author(
            id = it,
            name = authorInfo.name,
        )} }

        // Determine item type from Jellyfin Type field
        val itemType = if (Type == "Book") ItemType.Ebook else ItemType.AudioBook

        return Book(
            id = Id,
            title = Name,
            sortTitle = SortName,
            authors = authorNames,
            authorInfos = authorInfos,
            narrator = narratorPerson?.Name,
            narratorId = narratorPerson?.Id,
            description = Overview,
            coverImageId = if (ImageTags?.Primary != null) Id else null,
            duration = RunTimeTicks ?: 0L,
            chapters = Chapters?.map {
                Chapter(
                    name = it.Name,
                    startPositionTicks = it.StartPositionTicks,
                    imageId = it.ImageTag
                )
            } ?: emptyList(),
            userData = UserData?.let {
                com.kf7mxe.inglenook.UserData(
                    playbackPositionTicks = it.PlaybackPositionTicks ?: 0L,
                    playCount = it.PlayCount ?: 0,
                    isFavorite = it.IsFavorite ?: false,
                    played = it.Played ?: false,
                    lastPlayedDate = it.LastPlayedDate
                )
            },
            seriesName = SeriesName,
            seriesId = SeriesId,
            indexNumber = IndexNumber,
            year = ProductionYear,
            libraryId = ParentId,
            itemType = itemType,
            fileExtension = Path?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }?.let { ".$it" }
        )
    }

    companion object {
        private const val TICKS_PER_MILLISECOND = 10_000L

        /** Convert "Last, First" format (common in EPUB metadata) to "First Last". */
        fun normalizeAuthorName(name: String): String {
            val parts = name.split(",")
            if (parts.size == 2) {
                val last = parts[0].trim()
                val first = parts[1].trim()
                if (first.isNotEmpty() && last.isNotEmpty()) {
                    return "$first $last"
                }
            }
            return name
        }
    }
}

// Request/Response DTOs
@Serializable
data class AuthenticateRequest(val Username: String, val Pw: String)

@Serializable
data class AuthenticateResponse(
    val User: JellyfinUser,
    val AccessToken: String,
    val ServerId: String
)

@Serializable
data class JellyfinUser(val Id: String, val Name: String)

@Serializable
data class ServerInfoResponse(val Id: String, val ServerName: String)

@Serializable
data class ViewsResponse(val Items: List<JellyfinItem>)

@Serializable
data class ItemsResponse(val Items: List<JellyfinItem>, val TotalRecordCount: Int = 0)

@Serializable
data class JellyfinItem(
    val Id: String,
    val Name: String,
    val SortName: String? = null,
    val Type: String? = null,
    val Overview: String? = null,
    val RunTimeTicks: Long? = null,
    val ProductionYear: Int? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null,
    val IndexNumber: Int? = null,
    val ParentId: String? = null,
    val CollectionType: String? = null,
    val ImageTags: ImageTags? = null,
    val Chapters: List<JellyfinChapter>? = null,
    val People: List<JellyfinPerson>? = null,
    val UserData: JellyfinUserData? = null,
    // Additional author fields that Jellyfin may return
    val AlbumArtist: String? = null,
    val AlbumArtists: List<NameIdPair>? = null,
    val Artists: List<String>? = null,
    val ArtistItems: List<NameIdPair>? = null,

    val Path:String? = null,
    val RootItemId:String? = null


)

@Serializable
data class NameIdPair(
    val Name: String,
    val Id: String? = null
)

@Serializable
data class ImageTags(val Primary: String? = null)

@Serializable
data class JellyfinChapter(
    val Name: String,
    val StartPositionTicks: Long,
    val ImageTag: String? = null
)

// Chapter response from AudiobookChapters plugin endpoint
@Serializable
data class PluginChapter(
    val Name: String,
    val StartPositionTicks: Long,
    val ImageDateModified: String? = null
)

@Serializable
data class JellyfinPerson(
    val Name: String,
    val Id: String? = null,
    val Type: String? = null,
    val Role: String? = null
)

@Serializable
data class JellyfinUserData(
    val PlaybackPositionTicks: Long? = null,
    val PlayCount: Int? = null,
    val IsFavorite: Boolean? = null,
    val Played: Boolean? = null,
    val LastPlayedDate: String? = null
)

@Serializable
data class PlaybackStartInfo(val ItemId: String, val PositionTicks: Long)

@Serializable
data class PlaybackProgressInfo(val ItemId: String, val PositionTicks: Long, val IsPaused: Boolean)

@Serializable
data class PlaybackStopInfo(val ItemId: String, val PositionTicks: Long)

// Quick Connect DTOs
@Serializable
data class QuickConnectResult(
    val Secret: String? = null,
    val Code: String? = null,
    val Authenticated: Boolean? = null,
    val DateAdded: String? = null
)

@Serializable
data class QuickConnectAuthRequest(val Secret: String)

// Search results
data class SearchResults(
    val books: List<Book> = emptyList(),
    val authors: List<Author> = emptyList()
)
