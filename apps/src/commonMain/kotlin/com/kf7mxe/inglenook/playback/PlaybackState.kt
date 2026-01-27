package com.kf7mxe.inglenook.playback

import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.Chapter
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PlaybackState {
    // Current book being played
    val currentBook = Signal<AudioBook?>(null)

    // Playback state
    val isPlaying = Signal(false)
    val positionTicks = Signal(0L)
    val duration = Signal(0L)
    val playbackSpeed = Signal(1.0f)

    // Current chapter tracking
    val currentChapter = Signal<Chapter?>(null)

    // Skip amounts in ticks (10,000 ticks = 1ms)
    private const val SKIP_FORWARD_TICKS = 30 * 10_000_000L // 30 seconds
    private const val SKIP_BACKWARD_TICKS = 15 * 10_000_000L // 15 seconds

    // Progress sync job
    private var progressSyncJob: Job? = null

    // Platform audio player (expect/actual)
    private var audioPlayer: AudioPlayer? = null

    fun play(book: AudioBook, startPosition: Long = 0L) {
        currentBook.value = book
        duration.value = book.duration
        positionTicks.value = startPosition

        // Update current chapter
        updateCurrentChapter()

        // Start playback
        audioPlayer?.stop()
        audioPlayer = createAudioPlayer()
        audioPlayer?.play(book, startPosition)
        isPlaying.value = true

        // Start progress sync
        startProgressSync()

        // Report playback start to Jellyfin
        AppScope.launch {
            jellyfinClient.value?.reportPlaybackStart(book.id, startPosition)
        }
    }

    fun pause() {
        audioPlayer?.pause()
        isPlaying.value = false
        stopProgressSync()

        // Report playback progress to Jellyfin
        val book = currentBook.value
        if (book != null) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackProgress(book.id, positionTicks.value, true)
            }
        }
    }

    fun resume() {
        audioPlayer?.resume()
        isPlaying.value = true
        startProgressSync()

        // Report playback progress to Jellyfin
        val book = currentBook.value
        if (book != null) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackProgress(book.id, positionTicks.value, false)
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun stop() {
        val book = currentBook.value

        audioPlayer?.stop()
        audioPlayer = null
        isPlaying.value = false
        stopProgressSync()

        // Report playback stopped to Jellyfin
        if (book != null) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackStopped(book.id, positionTicks.value)
            }
        }

        currentBook.value = null
        positionTicks.value = 0L
        duration.value = 0L
        currentChapter.value = null
    }

    fun seek(newPositionTicks: Long) {
        val clampedPosition = newPositionTicks.coerceIn(0L, duration.value)
        positionTicks.value = clampedPosition
        audioPlayer?.seek(clampedPosition)
        updateCurrentChapter()
    }

    fun skipForward() {
        seek(positionTicks.value + SKIP_FORWARD_TICKS)
    }

    fun skipBackward() {
        seek(positionTicks.value - SKIP_BACKWARD_TICKS)
    }

    fun nextChapter() {
        val book = currentBook.value ?: return
        val chapters = book.chapters
        if (chapters.isEmpty()) return

        val current = currentChapter.value
        val currentIndex = chapters.indexOf(current)
        if (currentIndex >= 0 && currentIndex < chapters.size - 1) {
            val nextChapter = chapters[currentIndex + 1]
            seek(nextChapter.startPositionTicks)
        }
    }

    fun previousChapter() {
        val book = currentBook.value ?: return
        val chapters = book.chapters
        if (chapters.isEmpty()) return

        val current = currentChapter.value
        val currentIndex = chapters.indexOf(current)

        // If we're more than 3 seconds into the current chapter, restart it
        // Otherwise, go to the previous chapter
        val chapterStartTicks = current?.startPositionTicks ?: 0L
        val ticksIntoChapter = positionTicks.value - chapterStartTicks
        val threeSeconds = 3 * 10_000_000L

        if (ticksIntoChapter > threeSeconds || currentIndex <= 0) {
            // Restart current chapter
            seek(chapterStartTicks)
        } else {
            // Go to previous chapter
            val prevChapter = chapters[currentIndex - 1]
            seek(prevChapter.startPositionTicks)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed.value = speed
        audioPlayer?.setPlaybackSpeed(speed)
    }

    private fun updateCurrentChapter() {
        val book = currentBook.value ?: return
        val chapters = book.chapters
        if (chapters.isEmpty()) {
            currentChapter.value = null
            return
        }

        val position = positionTicks.value
        currentChapter.value = chapters.lastOrNull { it.startPositionTicks <= position }
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = AppScope.launch {
            while (true) {
                delay(10_000) // Sync every 10 seconds

                val book = currentBook.value
                if (book != null && isPlaying.value) {
                    // Update position from audio player
                    audioPlayer?.let {
                        positionTicks.value = it.getCurrentPosition()
                    }
                    updateCurrentChapter()

                    // Report to Jellyfin
                    jellyfinClient.value?.reportPlaybackProgress(
                        book.id,
                        positionTicks.value,
                        !isPlaying.value
                    )
                }
            }
        }
    }

    private fun stopProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = null
    }

    // Update position (called by platform audio player)
    fun onPositionUpdate(newPositionTicks: Long) {
        positionTicks.value = newPositionTicks
        updateCurrentChapter()
    }

    // Handle playback completion
    fun onPlaybackComplete() {
        val book = currentBook.value
        if (book != null) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackStopped(book.id, duration.value)
            }
        }
        stop()
    }
}

// Platform-specific audio player interface
expect fun createAudioPlayer(): AudioPlayer

interface AudioPlayer {
    fun play(book: AudioBook, startPositionTicks: Long)
    fun pause()
    fun resume()
    fun stop()
    fun seek(positionTicks: Long)
    fun setPlaybackSpeed(speed: Float)
    fun getCurrentPosition(): Long
}
