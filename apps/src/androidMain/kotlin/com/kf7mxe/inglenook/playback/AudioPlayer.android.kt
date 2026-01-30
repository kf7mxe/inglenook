package com.kf7mxe.inglenook.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.views.AndroidAppContext

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

class AndroidAudioPlayer : AudioPlayer {
    private var exoPlayer: ExoPlayer? = null
    private var currentBookId: String? = null

    private fun getContext(): Context = AndroidAppContext.applicationCtx

    private fun ensurePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(getContext()).build().also {
            exoPlayer = it

            // Add listener for playback events
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        PlaybackState.onPlaybackComplete()
                    }
                }
            })
        }
    }

    override fun play(book: AudioBook, startPositionTicks: Long) {
        currentBookId = book.id

        // Get stream URL from Jellyfin client
        val streamUrl = jellyfinClient.value?.getAudioStreamUrl(book.id) ?: return

        val player = ensurePlayer()

        // Create media item
        val mediaItem = MediaItem.fromUri(streamUrl)

        // Set media and prepare
        player.setMediaItem(mediaItem)
        player.prepare()

        // Seek to start position (convert ticks to ms: ticks / 10_000)
        player.seekTo(startPositionTicks / 10_000)

        // Start playback
        player.play()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun resume() {
        exoPlayer?.play()
    }

    override fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentBookId = null
    }

    override fun seek(positionTicks: Long) {
        // Convert ticks to ms
        exoPlayer?.seekTo(positionTicks / 10_000)
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    override fun getCurrentPosition(): Long {
        // Convert ms to ticks
        return (exoPlayer?.currentPosition ?: 0L) * 10_000
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        currentBookId = null
    }
}
