package com.kf7mxe.inglenook.playback

import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

class AndroidAudioPlayer : AudioPlayer {
    // TODO: Implement using Media3 ExoPlayer with MediaSession for background playback
    // This is a stub implementation

    private var currentBookId: String? = null
    private var currentPosition: Long = 0L
    private var isPlaying: Boolean = false

    override fun play(book: AudioBook, startPositionTicks: Long) {
        currentBookId = book.id
        currentPosition = startPositionTicks
        isPlaying = true

        // TODO: Initialize ExoPlayer and start playback
        // val streamUrl = jellyfinClient.value?.getAudioStreamUrl(book.id)
        // exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        // exoPlayer.seekTo(startPositionTicks / 10_000) // Convert ticks to ms
        // exoPlayer.prepare()
        // exoPlayer.play()
    }

    override fun pause() {
        isPlaying = false
        // TODO: exoPlayer.pause()
    }

    override fun resume() {
        isPlaying = true
        // TODO: exoPlayer.play()
    }

    override fun stop() {
        isPlaying = false
        currentBookId = null
        currentPosition = 0L
        // TODO: exoPlayer.stop()
    }

    override fun seek(positionTicks: Long) {
        currentPosition = positionTicks
        // TODO: exoPlayer.seekTo(positionTicks / 10_000) // Convert ticks to ms
    }

    override fun setPlaybackSpeed(speed: Float) {
        // TODO: exoPlayer.setPlaybackSpeed(speed)
    }

    override fun getCurrentPosition(): Long {
        // TODO: return exoPlayer.currentPosition * 10_000 // Convert ms to ticks
        return currentPosition
    }
}
