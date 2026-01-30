package com.kf7mxe.inglenook.jellyfin

import com.kf7mxe.inglenook.*
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

    private fun getAuthHeader(): String {
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

    suspend fun getAllBooks(libraryId: String? = null): List<AudioBook> {
        val uid = userId ?: return emptyList()
        val parentId = libraryId ?: selectedLibraryId.value

        val url = buildString {
            append("$serverUrl/Users/$uid/Items")
            append("?IncludeItemTypes=AudioBook")
            append("&Recursive=true")
            append("&Fields=Chapters,Overview,People")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            if (parentId != null) {
                append("&ParentId=$parentId")
            }
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

        val url = buildString {
            append("$serverUrl/Users/$uid/Items/Resume")
            append("?IncludeItemTypes=AudioBook")
            append("&Fields=Chapters,Overview,People")
            append("&Limit=10")
        }

        val response = client.get(url) {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val itemsResponse: ItemsResponse = response.body()
        return itemsResponse.Items.map { it.toAudioBook() }
    }

    suspend fun getRecentlyAddedBooks(): List<AudioBook> {
        val uid = userId ?: return emptyList()
        val parentId = selectedLibraryId.value

        val url = buildString {
            append("$serverUrl/Users/$uid/Items/Latest")
            append("?IncludeItemTypes=AudioBook")
            append("&Fields=Chapters,Overview,People")
            append("&Limit=20")
            if (parentId != null) {
                append("&ParentId=$parentId")
            }
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

        val url = buildString {
            append("$serverUrl/Users/$uid/Suggestions")
            append("?IncludeItemTypes=AudioBook")
            append("&Fields=Chapters,Overview,People")
            append("&Limit=10")
        }

        val response = client.get(url) {
            header("X-Emby-Authorization", getAuthHeader())
        }

        if (!response.status.isSuccess()) return emptyList()

        val itemsResponse: ItemsResponse = response.body()
        return itemsResponse.Items.map { it.toAudioBook() }
    }

    suspend fun getBook(itemId: String): AudioBook? {
        val uid = userId ?: return null

        val response = client.get("$serverUrl/Users/$uid/Items/$itemId") {
            header("X-Emby-Authorization", getAuthHeader())
            parameter("Fields", "Chapters,Overview,People")
        }

        if (!response.status.isSuccess()) return null

        val item: JellyfinItem = response.body()
        return item.toAudioBook()
    }

    suspend fun getAuthors(): List<Author> {
        val uid = userId ?: return emptyList()
        val parentId = selectedLibraryId.value

        val url = buildString {
            append("$serverUrl/Artists/AlbumArtists")
            append("?UserId=$uid")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            if (parentId != null) {
                append("&ParentId=$parentId")
            }
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
            append("?IncludeItemTypes=AudioBook")
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
        return AudioBook(
            id = Id,
            title = Name,
            sortTitle = SortName,
            authors = People?.filter { it.Type == "Author" }?.map { it.Name } ?: emptyList(),
            narrator = People?.find { it.Type == "Narrator" }?.Name,
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
            libraryId = ParentId
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
    val UserData: JellyfinUserData? = null
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
