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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.MainActivity
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import java.lang.ref.WeakReference

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

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

        // Create MediaSession with custom commands
        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCustomLayout(listOf(skipBackButton, skipForwardButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(skipBackCommand)
                                .add(skipForwardCommand)
                                .build()
                        )
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
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
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            // Stop the service if playback is paused
            stopSelf()
        }
    }

    override fun onDestroy() {
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

        // Use local file if available (offline playback), otherwise stream from server
        val mediaUri = if (localFilePath != null) {
            // Use local file URI for offline playback
            "file://$localFilePath"
        } else {
            // Stream from server with HLS for fast start — server begins transcoding at start position
            jellyfinClient.value?.getAudioStreamUrl(book.id, startPositionTicks, useHls = true) ?: return
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

        // Create media item with metadata
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(startPositionTicks / 10_000) // Convert ticks to ms
        player.play()
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

        private var instanceRef: WeakReference<PlaybackService>? = null

        fun getInstance(): PlaybackService? = instanceRef?.get()
    }

    init {
        instanceRef = WeakReference(this)
    }
}
