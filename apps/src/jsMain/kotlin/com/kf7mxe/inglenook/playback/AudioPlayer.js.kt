package com.kf7mxe.inglenook.playback

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.downloads.PlatformDownloader
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import kotlinx.browser.window
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import org.w3c.dom.Audio
import org.w3c.dom.events.Event

actual fun createAudioPlayer(): AudioPlayer = JsAudioPlayer()

class JsAudioPlayer : AudioPlayer {
    private var audioElement: Audio? = null
    private var currentBookId: String? = null
    private var positionUpdateInterval: Int? = null

    override fun play(book: Book, startPositionTicks: Long, localFilePath: String?) {
        // Stop any existing playback
        stop()

        currentBookId = book.id

        // Check if we have an IndexedDB path (offline download)
        if (localFilePath != null && localFilePath.startsWith("indexeddb://")) {
            // Need to fetch blob URL asynchronously
            val bookId = localFilePath.removePrefix("indexeddb://")
            AppScope.launch {
                val blobUrl = PlatformDownloader.getBlobUrl(bookId)
                if (blobUrl != null) {
                    startPlayback(book, blobUrl, startPositionTicks)
                } else {
                    // Fallback to streaming if blob not found
                    val streamUrl = jellyfinClient.value?.getAudioStreamUrl(book.id) ?: return@launch
                    startPlayback(book, streamUrl, startPositionTicks)
                }
            }
            return
        }

        // Use local file if available (already a blob URL), otherwise stream from server
        val mediaUrl = localFilePath ?: jellyfinClient.value?.getAudioStreamUrl(book.id) ?: return
        startPlayback(book, mediaUrl, startPositionTicks)
    }

    private fun startPlayback(book: Book, mediaUrl: String, startPositionTicks: Long) {
        currentBookId = book.id

        // Create audio element
        audioElement = Audio(mediaUrl).apply {
            // Set up event handlers
            onended = { _: Event ->
                PlaybackState.onPlaybackComplete()
            }

            ontimeupdate = { _: Event ->
                val currentTimeSeconds = currentTime
                val positionTicks = (currentTimeSeconds * 10_000_000).toLong()

                AppScope.launch {
                    PlaybackState.onPositionUpdate(positionTicks)
                }
            }

            // Error handling is done via the error event listener
            addEventListener("error", { event ->
                console.log("Audio error:", event)
            })

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
        positionUpdateInterval?.let { window.clearInterval(it) }
        positionUpdateInterval = null

        audioElement?.pause()
        audioElement?.src = ""
        audioElement = null
        currentBookId = null

        // Clear media session
        clearMediaSession()
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

    private fun setupMediaSession(book: Book) {
        // Use Media Session API for browser media controls
        try {
            val title = book.title
            val artist = book.authors.joinToString(", ")
            val coverUrl = book.coverImageId?.let { id ->
                jellyfinClient.value?.getImageUrl(id)
            }

            js("""
                if ('mediaSession' in navigator) {
                    navigator.mediaSession.metadata = new MediaMetadata({
                        title: title,
                        artist: artist,
                        artwork: coverUrl ? [{ src: coverUrl, sizes: '512x512', type: 'image/jpeg' }] : []
                    });

                    navigator.mediaSession.setActionHandler('play', function() {
                        var audio = document.querySelector('audio');
                        if (audio) audio.play();
                    });

                    navigator.mediaSession.setActionHandler('pause', function() {
                        var audio = document.querySelector('audio');
                        if (audio) audio.pause();
                    });

                    navigator.mediaSession.setActionHandler('seekbackward', function() {
                        var audio = document.querySelector('audio');
                        if (audio) audio.currentTime = Math.max(0, audio.currentTime - 30);
                    });

                    navigator.mediaSession.setActionHandler('seekforward', function() {
                        var audio = document.querySelector('audio');
                        if (audio) audio.currentTime = audio.currentTime + 30;
                    });

                    navigator.mediaSession.setActionHandler('stop', function() {
                        var audio = document.querySelector('audio');
                        if (audio) {
                            audio.pause();
                            audio.currentTime = 0;
                        }
                    });
                }
            """)
        } catch (e: Throwable) {
            console.log("MediaSession setup failed: ${e.message}")
        }
    }

    private fun clearMediaSession() {
        try {
            js("""
                if ('mediaSession' in navigator) {
                    navigator.mediaSession.metadata = null;
                    navigator.mediaSession.setActionHandler('play', null);
                    navigator.mediaSession.setActionHandler('pause', null);
                    navigator.mediaSession.setActionHandler('seekbackward', null);
                    navigator.mediaSession.setActionHandler('seekforward', null);
                    navigator.mediaSession.setActionHandler('stop', null);
                }
            """)
        } catch (e: Throwable) {
            // Ignore
        }
    }
}
