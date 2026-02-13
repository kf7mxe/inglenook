package com.kf7mxe.inglenook

import com.lightningkite.services.data.*
import com.lightningkite.services.database.HasId
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Jellyfin Server Configuration
@Serializable
data class JellyfinServerConfig(
    val _id: Uuid = Uuid.random(),
    val serverUrl: String,
    val userId: String,
    val username: String,
    val accessToken: String,
    val deviceId: String,
    val serverId: String? = null,
    val serverName: String? = null
) {
    /** Stable key for scoping per-server persistent data. */
    val storageKey: String get() = _id.toString()

    /** Display name: serverName if available, otherwise the URL host. */
    val displayName: String get() = serverName ?: serverUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
}

// Item type enum for distinguishing audiobooks from ebooks
@Serializable
enum class ItemType {
    AudioBook,
    Ebook
}

// Audio Book representation from Jellyfin (also used for Ebooks)
@Serializable
data class AudioBook(
    val id: String,
    val title: String,
    val sortTitle: String? = null,
    val authors: List<Author> = emptyList(),
    val authorInfos: List<AuthorInfo> = emptyList(), // Authors with IDs for linking
    val narrator: String? = null,
    val narratorId: String? = null, // Narrator ID for linking
    val description: String? = null,
    val coverImageId: String? = null,
    val duration: Long = 0L, // Duration in ticks (10,000 ticks = 1ms)
    val chapters: List<Chapter> = emptyList(),
    val userData: UserData? = null,
    val seriesName: String? = null,
    val seriesId: String? = null,
    val indexNumber: Int? = null, // Book number in series
    val year: Int? = null,
    val libraryId: String? = null,
    val itemType: ItemType = ItemType.AudioBook // Distinguishes audiobooks from ebooks
)

// Chapter information
@Serializable
data class Chapter(
    val name: String,
    val startPositionTicks: Long,
    val imageId: String? = null
)

// User playback data from Jellyfin
@Serializable
data class UserData(
    val playbackPositionTicks: Long = 0L,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val played: Boolean = false,
    val lastPlayedDate: String? = null
)

// Author/Person representation
@Serializable
data class Author(
    val id: String,
    val name: String,
    val imageId: String? = null,
    val overview: String? = null
)

// Author info stored with audiobook (name + optional id for linking)
@Serializable
data class AuthorInfo(
    val name: String,
    val id: String? = null
)

// Series representation
@Serializable
data class Series(
    val id: String,
    val name: String,
    val imageId: String? = null,
    val bookCount: Int = 0,
    val overview: String? = null
)

// Library/Collection
@Serializable
data class JellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String? = null,
    val imageId: String? = null
)

// Local Bookshelf (stored on device)
@GenerateDataClassPaths
@Serializable
data class Bookshelf(
    override val _id: Uuid = Uuid.random(),
    val name: String,
    val bookIds: List<String> = emptyList(),
    val coverImageUrl: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
) : HasId<Uuid>

// Local playback progress (stored on device for offline tracking)
@GenerateDataClassPaths
@Serializable
data class PlaybackProgress(
    override val _id: String, // bookId
    val positionTicks: Long = 0L,
    val lastPlayed: Instant = Clock.System.now(),
    val duration: Long = 0L,
    val synced: Boolean = false // Whether synced to Jellyfin
) : HasId<String>

// Downloaded book information
@GenerateDataClassPaths
@Serializable
data class DownloadedBook(
    override val _id: String, // bookId
    val title: String,
    val authors: List<Author> = emptyList(),
    val localFilePath: String,
    val coverImagePath: String? = null,
    val downloadedAt: Instant = Clock.System.now(),
    val fileSize: Long = 0L,
    val duration: Long = 0L,
    val chapters: List<Chapter> = emptyList()
) : HasId<String>

// Download progress tracking
@Serializable
data class DownloadProgress(
    val bookId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus
)

@Serializable
enum class DownloadStatus {
    Pending,
    Downloading,
    Completed,
    Failed,
    Cancelled
}

// Bookmark for audiobooks
@GenerateDataClassPaths
@Serializable
data class Bookmark(
    override val _id: Uuid = Uuid.random(),
    val bookId: String,
    val positionTicks: Long,
    val note: String? = null,
    val chapterName: String? = null, // For display purposes
    val createdAt: Instant = Clock.System.now()
) : HasId<Uuid>

// Theme preset enumeration
@Serializable
enum class ThemePreset(val displayName: String, val allowsCustomization: Boolean = true) {
    Cozy("Cozy", true),           // Forest green, warm background
    AutumnCabin("Autumn Cabin", true),          // Blue tones
    Midnight("Midnight", true),    // Dark theme
    Sunrise("Sunrise", true),      // Warm orange
    Material("Material", true),    // Material design style
    Hackerman("Hackerman", true),  // Terminal/monochrome style
    Clouds("Clouds", true),        // Soft rounded style
    Obsidian("Obsidian", true),    // Dark with gradients
    Custom("Custom", true)         // Fully user customizable
}

// Theme settings for customization
@Serializable
data class ThemeSettings(
    val primaryColor: String? = null,      // Hex color for accent
    val secondaryColor: String? = null,    // Hex color for background tint
    val accentColor: String? = null,       // Hex color for outlines
    val baseOpacity: Float = 0.9f,         // Background opacity (0-1)
    val opacityStep: Float = 0.1f,         // Opacity increase per card level
    val outlineOpacity: Float = 0.6f,      // Outline visibility (0-1)
    // Layout customizations
    val cornerRadius: Float = 0.5f,        // Corner radius in rem
    val padding: Float = 0.75f,            // Padding in rem
    val gap: Float = 0.75f,                // Gap between elements in rem
    val elevation: Float = 0f,             // Elevation in dp
    val outlineWidth: Float = 1f,          // Outline width in dp
    // Wallpaper
    val wallpaperPath: String? = null,     // Local file path for wallpaper
    val wallpaperBlurRadius: Float = 0f,
    // Blurred background
    val showPlayingBookCoverAsWallpaper: Boolean = false,  // Enable blurred cover image background
    val showPlayingBookCoverOnNowPlayingAndBookDetail: Boolean = false,
    val blurRadius: Float = 20f,            // Blur radius for cover image background

    val imageSemanticSettings:ImageSemanticSettings? = ImageSemanticSettings(cornerRadius= 0.5f),

)

@Serializable
data class ImageSemanticSettings(
    val cornerRadius: Float,

)

// App settings stored locally
@Serializable
data class AppSettings(
    val themePreset: ThemePreset = ThemePreset.Cozy,
    val themeSettings: ThemeSettings = ThemeSettings(),
    val customAccentColor: String? = null,
    val downloadOverWifiOnly: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val skipForwardSeconds: Int = 30,
    val skipBackwardSeconds: Int = 15,
    val sleepTimerMinutes: Int? = null
)



const val databaseVersion = 1

@Serializable
data class ModelTableVersionContainer(val version: Int = databaseVersion, val table: String)
