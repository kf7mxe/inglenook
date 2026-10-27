package com.kf7mxe.inglenook.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.MainActivity
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.jellyfin.initializeJellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Android Auto can start this service on a cold process where the UI
        // (which normally initializes the client) has never run.
        ensureClientInitialized()

        // Create ExoPlayer with audio attributes for audiobooks
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                1000,  // Start playback after 1s of buffer (default 2500ms)
                2000   // After rebuffer, start after 2s (default 5000ms)
            )
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // true for handleAudioFocus
            .setHandleAudioBecomingNoisy(true) // Pause when headphones unplugged
            .build()
            .also { player ->
                // Add listener for playback events
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> PlaybackState.isBuffering.value = true
                            Player.STATE_READY -> PlaybackState.isBuffering.value = false
                            Player.STATE_ENDED -> PlaybackState.onPlaybackComplete()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        PlaybackState.isPlaying.value = isPlaying
                    }
                })
            }

        // Create pending intent to launch app when notification is tapped
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Custom commands for skip forward/backward
        val skipBackCommand = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
        val skipForwardCommand = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)

        val skipBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
            .setDisplayName("Skip back 15s")
            .setSessionCommand(skipBackCommand)
            .build()
        val skipForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setDisplayName("Skip forward 30s")
            .setSessionCommand(skipForwardCommand)
            .build()

        // Create the library session (a MediaSession with a browse tree for Android Auto)
        mediaSession = MediaLibrarySession.Builder(this, exoPlayer!!, libraryCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCustomLayout(listOf(skipBackButton, skipForwardButton))
            .build()
    }

    // --- Browse tree for Android Auto ---

    private val libraryCallback = object : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            ensureClientInitialized()
            val rootMetadata = MediaMetadata.Builder()
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setTitle("Audiobooks")
                .build()
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(rootMetadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            libraryRootId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            if (libraryRootId != ROOT_ID) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                return future
            }
            serviceScope.launch {
                try {
                    val books = loadBooks()
                    val slice = books.drop(page.coerceAtLeast(0) * pageSize)
                        .take(pageSize.coerceAtLeast(0))
                    future.set(LibraryResult.ofItemList(slice.map { bookItem(it) }, params))
                } catch (e: CancellationException) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                    throw e
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                try {
                    val book = loadBooks().find { it.id == mediaId }
                    if (book != null) {
                        future.set(LibraryResult.ofItem(bookItem(book), null))
                    } else {
                        future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                    }
                } catch (e: CancellationException) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                    throw e
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        // Called when a browse item is tapped for playback (both the modern controller
        // path and Auto's legacy playFromMediaId route funnel through here).
        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    val books = loadBooks()
                    val resolved = mediaItems.mapNotNull { item ->
                        books.find { it.id == item.mediaId }
                    }
                    if (resolved.isEmpty()) {
                        future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                        return@launch
                    }
                    val startBook = resolved.getOrNull(startIndex) ?: resolved.first()
                    val savedMs = PlaybackState.getLocalPosition(startBook.id) / 10_000
                    val startMs = if (startPositionMs > 0) startPositionMs else savedMs
                    // Play through PlaybackState so app state, progress reporting and
                    // up-next keep working, then hand the session the real stream items
                    // (identical to what play() sets) in case it applies them itself.
                    PlaybackState.play(startBook, startMs * 10_000)
                    val realItems = resolved.mapNotNull { book ->
                        buildMediaItemOrNull(
                            book,
                            PlaybackState.getLocalPosition(book.id),
                            DownloadManager.getLocalFilePath(book.id)
                        )
                    }
                    if (realItems.isEmpty()) {
                        future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startMs))
                    } else {
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                realItems,
                                startIndex.coerceIn(0, realItems.size - 1),
                                startMs
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                    throw e
                } catch (e: Exception) {
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                }
            }
            return future
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY))
                        .build()
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SKIP_BACK -> {
                    val player = session.player
                    player.seekTo(maxOf(0, player.currentPosition - 15_000))
                }
                ACTION_SKIP_FORWARD -> {
                    val player = session.player
                    player.seekTo(player.currentPosition + 30_000)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    // Browse item: playable, identified by the Jellyfin book id (no URI — the URI is
    // resolved in onSetMediaItems when the item is actually played).
    private fun bookItem(book: Book): MediaItem {
        val imageUrl = book.coverImageId?.let { jellyfinClient.value?.getImageUrl(it, book.id) }
        val metadata = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.authors.joinToString(", ") { it.name })
            .setAlbumTitle(book.seriesName)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .apply { if (imageUrl != null) setArtworkUri(android.net.Uri.parse(imageUrl)) }
            .build()
        return MediaItem.Builder()
            .setMediaId(book.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun loadBooks(): List<Book> {
        ensureClientInitialized()
        return jellyfinClient.value?.getAllBooks() ?: emptyList()
    }

    private fun ensureClientInitialized() {
        if (jellyfinClient.value == null) initializeJellyfinClient()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            // Save position before stopping service
            val currentPosMs = player.currentPosition
            PlaybackState.positionTicks.value = currentPosMs * 10_000 // Convert ms to ticks
            PlaybackState.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }

    // Public methods for controlling playback from the app

    fun playBook(book: Book, startPositionTicks: Long, localFilePath: String? = null) {
        println("DEBUG playBook Playback service")
        val player = exoPlayer ?: return
        val mediaItem = buildMediaItemOrNull(book, startPositionTicks, localFilePath) ?: return

        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(startPositionTicks / 10_000) // Convert ticks to ms
        player.play()
    }

    // Builds the playable stream/local media item for [book], or null when no
    // stream URL is available (e.g. no active client).
    private fun buildMediaItemOrNull(book: Book, startPositionTicks: Long, localFilePath: String?): MediaItem? {
        // Use local file if available (offline playback), otherwise stream from server
        val mediaUri = if (localFilePath != null) {
            "file://$localFilePath"
        } else {
            // Stream from server with HLS for fast start — server begins transcoding at start position
            jellyfinClient.value?.getAudioStreamUrl(book.id, startPositionTicks, useHls = true) ?: return null
        }

        val imageUrl = book.coverImageId?.let { coverId ->
            jellyfinClient.value?.getImageUrl(coverId, book.id)
        }

        // Build media metadata
        val metadata = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.authors.map{it.name}.joinToString(", "))
            .setAlbumTitle(book.seriesName)
            .apply {
                if (imageUrl != null) {
                    setArtworkUri(android.net.Uri.parse(imageUrl))
                }
            }
            .build()

        return MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        println("DEBUG playback service ")
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
    }

    fun seek(positionTicks: Long) {
        exoPlayer?.seekTo(positionTicks / 10_000) // Convert ticks to ms
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    fun getCurrentPositionTicks(): Long {
        return (exoPlayer?.currentPosition ?: 0L) * 10_000 // Convert ms to ticks
    }

    companion object {
        private const val ACTION_SKIP_BACK = "com.kf7mxe.inglenook.SKIP_BACK"
        private const val ACTION_SKIP_FORWARD = "com.kf7mxe.inglenook.SKIP_FORWARD"
        private const val ROOT_ID = "audiobooks"

        private var instanceRef: WeakReference<PlaybackService>? = null

        fun getInstance(): PlaybackService? = instanceRef?.get()
    }

    init {
        instanceRef = WeakReference(this)
    }
}
