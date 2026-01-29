package com.kf7mxe.inglenook.playback

import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import org.w3c.dom.Audio
import kotlinx.browser.document

actual fun createAudioPlayer(): AudioPlayer = JsAudioPlayer()

class JsAudioPlayer : AudioPlayer {
    private var audioElement: Audio? = null
    private var currentBookId: String? = null

    override fun play(book: AudioBook, startPositionTicks: Long) {
        // Stop any existing playback
        stop()

        currentBookId = book.id

        // Get stream URL
        val streamUrl = jellyfinClient.value?.getAudioStreamUrl(book.id) ?: return

        // Create audio element
        audioElement = Audio(streamUrl).apply {
            // Seek to start position
            currentTime = startPositionTicks.toDouble() / 10_000_000.0 // Convert ticks to seconds

            // Set up Media Session API for browser media controls
            setupMediaSession(book)

            // Start playback
            play()
        }
    }

    override fun pause() {
        audioElement?.pause()
    }

    override fun resume() {
        audioElement?.play()
    }

    override fun stop() {
        audioElement?.pause()
        audioElement?.src = ""
        audioElement = null
        currentBookId = null
    }

    override fun seek(positionTicks: Long) {
        audioElement?.currentTime = positionTicks.toDouble() / 10_000_000.0
    }

    override fun setPlaybackSpeed(speed: Float) {
        audioElement?.playbackRate = speed.toDouble()
    }

    override fun getCurrentPosition(): Long {
        val seconds = audioElement?.currentTime ?: 0.0
        return (seconds * 10_000_000).toLong()
    }

    private fun setupMediaSession(book: AudioBook) {
        // Use Media Session API for browser media controls
        // This enables play/pause from browser UI and potentially lock screen
        try {
            js("""
        if ('mediaSession' in navigator) {
            navigator.mediaSession.metadata = new MediaMetadata({
                title: title,
                artist: artist,
                album: album
            });

            navigator.mediaSession.setActionHandler('play', function() {
                var audio = document.querySelector('audio');
                if (audio) audio.play();
            });

            navigator.mediaSession.setActionHandler('pause', function() {
                var audio = document.querySelector('audio');
                if (audio) audio.pause();
            });

            // ... rest of your handlers
        }
    """)
        } catch (e: Throwable) {
            println("MediaSession setup failed: ${e.message}")
        }
    }
}
