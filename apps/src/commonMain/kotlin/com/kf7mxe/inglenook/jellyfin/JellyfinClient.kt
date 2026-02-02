package com.kf7mxe.inglenook.jellyfin

import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.AuthorInfo
import com.kf7mxe.inglenook.cache.ApiCache
import io.ktor.client.*
import io.ktor.client.call.*
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

    suspend fun getLibraries(): List<JellyfinLibrary> {
        val uid = userId ?: return emptyList()

        val response = client.get("$serverUrl/Users/$uid/Views") {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val views: ViewsResponse = response.body()
        return views.Items.map {
            JellyfinLibrary(
                id = it.Id,
                name = it.Name,
                collectionType = it.CollectionType,
                imageId = it.ImageTags?.Primary
            )
        }
    }

    suspend fun getAllBooks(libraryId: String? = null, forceRefresh: Boolean = false): List<AudioBook> {
        val uid = userId ?: return emptyList()
        val libraryIds = if (libraryId != null) listOf(libraryId) else selectedLibraryIds.value
        val cacheKey = ApiCache.booksKey(libraryIds)

        return ApiCache.getOrPut(cacheKey, ApiCache.DEFAULT_TTL, forceRefresh) {
            fetchAllBooks(uid, libraryIds)
        }
    }

    private suspend fun fetchAllBooks(uid: String, libraryIds: List<String>): List<AudioBook> {
        // If specific libraries are selected, query each one and merge results
        if (libraryIds.isNotEmpty()) {
            val allBooks = mutableListOf<AudioBook>()
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
                    }
                } catch (e: Exception) {
                    // Continue with other libraries if one fails
                }
            }
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

    suspend fun getInProgressBooks(): List<AudioBook> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value

        // If specific libraries are selected, query each and merge
        if (libraryIds.isNotEmpty()) {
            val allInProgress = mutableListOf<AudioBook>()
            for (libId in libraryIds) {
                val url = buildString {
                    append("$serverUrl/Users/$uid/Items/Resume")
                    append("?IncludeItemTypes=AudioBook,Book")
                    append("&Recursive=true")
                    append("&Fields=Chapters,Overview,People")
                    append("&ParentId=$libId")
                    append("&Limit=20")
                }

                try {
                    val response = client.get(url) {
                        header("X-Emby-Authorization", getAuthHeader())
                    }
                    if (response.status.isSuccess()) {
                        val itemsResponse: ItemsResponse = response.body()
                        allInProgress.addAll(itemsResponse.Items.map { it.toAudioBook() })
                    }
                } catch (e: Exception) {
                    // Continue with other libraries
                }
            }
            return allInProgress.distinctBy { it.id }.take(10)
        }

        // No specific libraries - get all in progress
        val url = buildString {
            append("$serverUrl/Users/$uid/Items/Resume")
            append("?IncludeItemTypes=AudioBook,Book")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&Limit=20")
        }

        val response = client.get(url) {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val itemsResponse: ItemsResponse = response.body()
        return itemsResponse.Items.map { it.toAudioBook() }.take(10)
    }

    suspend fun getRecentlyAddedBooks(): List<AudioBook> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value

        // If specific libraries are selected, query each and merge
        if (libraryIds.isNotEmpty()) {
            val allBooks = mutableListOf<AudioBook>()
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
                    }
                } catch (e: Exception) {
                    // Continue with other libraries
                }
            }
            return allBooks.distinctBy { it.id }.take(20)
        }

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

        if (!response.status.isSuccess()) return emptyList()

        val items: List<JellyfinItem> = response.body()
        return items.map { it.toAudioBook() }
    }

    suspend fun getSuggestedBooks(): List<AudioBook> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value

        // If specific libraries are selected, query each and merge
        if (libraryIds.isNotEmpty()) {
            val allSuggestions = mutableListOf<AudioBook>()
            for (libId in libraryIds) {
                val url = buildString {
                    append("$serverUrl/Users/$uid/Suggestions")
                    append("?IncludeItemTypes=AudioBook,Book")
                    append("&Fields=Chapters,Overview,People")
                    append("&ParentId=$libId")
                    append("&Limit=20")
                }

                try {
                    val response = client.get(url) {
                        header("X-Emby-Authorization", getAuthHeader())
                    }
                    if (response.status.isSuccess()) {
                        val itemsResponse: ItemsResponse = response.body()
                        allSuggestions.addAll(itemsResponse.Items.map { it.toAudioBook() })
                    }
                } catch (e: Exception) {
                    // Continue with other libraries
                }
            }
            return allSuggestions.distinctBy { it.id }.take(10)
        }

        // No specific libraries - get all suggestions
        val url = buildString {
            append("$serverUrl/Users/$uid/Suggestions")
            append("?IncludeItemTypes=AudioBook,Book")
            append("&Fields=Chapters,Overview,People")
            append("&Limit=20")
        }

        val response = client.get(url) {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val itemsResponse: ItemsResponse = response.body()
        return itemsResponse.Items.map { it.toAudioBook() }.take(10)
    }

    suspend fun getBook(itemId: String): AudioBook? {
        val uid = userId ?: return null

        val response = client.get("$serverUrl/Users/$uid/Items/$itemId") {
            header("X-Emby-Authorization", getAuthHeader())
            parameter("Fields", "Chapters,Overview,People,Path,MediaSources")
        }

        if (!response.status.isSuccess()) return null

        val item: JellyfinItem = response.body()
        var book = item.toAudioBook()

        // If no chapters, try to find and parse a .cue file
        if (book.chapters.isEmpty()) {
            val cueChapters = tryParseCueFile(itemId, item.Path)
            if (cueChapters.isNotEmpty()) {
                book = book.copy(chapters = cueChapters)
            }
        }

        return book
    }

    /**
     * Try to find and parse a .cue file for chapter information.
     */
    private suspend fun tryParseCueFile(itemId: String, itemPath: String?): List<Chapter> {
        if (itemPath == null) return emptyList()

        try {
            // Try to get sibling files (files in same directory)
            val parentPath = itemPath.substringBeforeLast("/")
            val baseName = itemPath.substringAfterLast("/").substringBeforeLast(".")

            // Common .cue file naming patterns to try
            val cuePatterns = listOf(
                "$baseName.cue",
                "${baseName.lowercase()}.cue",
                "chapters.cue"
            )

            // Try to fetch the .cue file directly using the item's media source
            val cueUrl = "$serverUrl/Items/$itemId/File"

            // Actually, Jellyfin doesn't easily expose sibling files via API
            // A simpler approach: check if there's an external subtitle/chapter file
            // For now, we'll try the common pattern of baseName.cue

            for (pattern in cuePatterns) {
                val cueContent = fetchCueFile("$parentPath/$pattern")
                if (cueContent != null) {
                    val chapters = com.kf7mxe.inglenook.util.CueParser.parse(cueContent)
                    if (chapters.isNotEmpty()) {
                        return chapters
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors - .cue file parsing is optional
        }

        return emptyList()
    }

    /**
     * Try to fetch a .cue file from the server.
     */
    private suspend fun fetchCueFile(path: String): String? {
        return try {
            // This requires the file to be accessible via the Jellyfin API
            // which may not always work depending on server configuration
            val response = client.get("$serverUrl/Library/VirtualFolders") {
                header("X-Emby-Authorization", getAuthHeader())
            }

            // For now, return null as direct file access isn't straightforward
            // A more complete implementation would use the Items API with file paths
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAuthors(forceRefresh: Boolean = false): List<Author> {
        val uid = userId ?: return emptyList()
        val libraryIds = selectedLibraryIds.value
        val cacheKey = ApiCache.authorsKey(libraryIds)

        return ApiCache.getOrPut(cacheKey, ApiCache.DEFAULT_TTL, forceRefresh) {
            fetchAuthors(uid, libraryIds)
        }
    }

    private suspend fun fetchAuthors(uid: String, libraryIds: List<String>): List<Author> {
        // If specific libraries are selected, query each and merge
        if (libraryIds.isNotEmpty()) {
            val allAuthors = mutableListOf<Author>()
            for (libId in libraryIds) {
                val url = buildString {
                    append("$serverUrl/Artists/AlbumArtists")
                    append("?UserId=$uid")
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
                        allAuthors.addAll(itemsResponse.Items.map {
                            Author(
                                id = it.Id,
                                name = it.Name,
                                imageId = it.ImageTags?.Primary,
                                overview = it.Overview
                            )
                        })
                    }
                } catch (e: Exception) {
                    // Continue with other libraries
                }
            }
            return allAuthors.distinctBy { it.id }.sortedBy { it.name }
        }

        // No specific libraries - get all authors
        val url = buildString {
            append("$serverUrl/Artists/AlbumArtists")
            append("?UserId=$uid")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }

        val response = client.get(url) {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val itemsResponse: ItemsResponse = response.body()
        return itemsResponse.Items.map {
            Author(
                id = it.Id,
                name = it.Name,
                imageId = it.ImageTags?.Primary,
                overview = it.Overview
            )
        }
    }

    suspend fun getAuthor(authorId: String): Author? {
        val uid = userId ?: return null

        val response = client.get("$serverUrl/Users/$uid/Items/$authorId") {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return null

        val item: JellyfinItem = response.body()
        return Author(
            id = item.Id,
            name = item.Name,
            imageId = item.ImageTags?.Primary,
            overview = item.Overview
        )
    }

    suspend fun getBooksByAuthor(authorId: String): List<AudioBook> {
        val uid = userId ?: return emptyList()

        val url = buildString {
            append("$serverUrl/Users/$uid/Items")
            append("?IncludeItemTypes=AudioBook,Book")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&ArtistIds=$authorId")
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
    suspend fun getBooksBySeries(seriesName: String): List<AudioBook> {
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

    fun getAudioStreamUrl(itemId: String): String {
        return buildString {
            append("$serverUrl/Audio/$itemId/universal")
            append("?UserId=$userId")
            append("&DeviceId=$deviceId")
            append("&api_key=$accessToken")
            append("&Container=opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg")
            append("&TranscodingContainer=ts")
            append("&AudioCodec=aac")
        }
    }

    suspend fun search(query: String, limit: Int = 20): SearchResults {
        val uid = userId ?: return SearchResults()
        val libraryIds = selectedLibraryIds.value

        // Search for audiobooks and ebooks
        val booksUrl = buildString {
            append("$serverUrl/Users/$uid/Items")
            append("?SearchTerm=$query")
            append("&IncludeItemTypes=AudioBook,Book")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&Limit=$limit")
        }

        val books = try {
            val response = client.get(booksUrl) {
                header("X-Emby-Authorization", getAuthHeader())
            }
            if (response.status.isSuccess()) {
                val itemsResponse: ItemsResponse = response.body()
                val allBooks = itemsResponse.Items.map { it.toAudioBook() }
                // Filter by selected libraries if any
                if (libraryIds.isEmpty()) allBooks else allBooks.filter { it.libraryId in libraryIds }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
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

    private fun JellyfinItem.toAudioBook(): AudioBook {
        // Jellyfin uses various types for audiobook authors: Author, AlbumArtist, Artist, Writer
        val authorTypes = setOf("Author", "AlbumArtist", "Artist", "Writer")
        val authorPeople = People?.filter { it.Type in authorTypes } ?: emptyList()
        val narratorPerson = People?.find { it.Type == "Narrator" }

        // Build author list from multiple sources (People array, AlbumArtists, ArtistItems, etc.)
        val authorInfos = when {
            authorPeople.isNotEmpty() -> authorPeople.map { AuthorInfo(name = it.Name, id = it.Id) }
            AlbumArtists?.isNotEmpty() == true -> AlbumArtists.map { AuthorInfo(name = it.Name, id = it.Id) }
            ArtistItems?.isNotEmpty() == true -> ArtistItems.map { AuthorInfo(name = it.Name, id = it.Id) }
            AlbumArtist != null -> listOf(AuthorInfo(name = AlbumArtist, id = null))
            Artists?.isNotEmpty() == true -> Artists.map { AuthorInfo(name = it, id = null) }
            else -> emptyList()
        }
        val authorNames = authorInfos.map { it.name }

        // Determine item type from Jellyfin Type field
        val itemType = if (Type == "Book") ItemType.Ebook else ItemType.AudioBook

        return AudioBook(
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
            itemType = itemType
        )
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
    val books: List<AudioBook> = emptyList(),
    val authors: List<Author> = emptyList()
)
