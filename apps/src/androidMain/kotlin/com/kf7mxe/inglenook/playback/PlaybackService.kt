package com.kf7mxe.inglenook.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.MainActivity
import com.kf7mxe.inglenook.jellyfin.jellyfinClient

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
                        if (playbackState == Player.STATE_ENDED) {
                            PlaybackState.onPlaybackComplete()
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

        // Create MediaSession
        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(sessionActivityPendingIntent)
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

    fun playBook(book: AudioBook, startPositionTicks: Long, localFilePath: String? = null) {
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
        private var instance: PlaybackService? = null

        fun getInstance(): PlaybackService? = instance
    }

    init {
        instance = this
    }
}
