package com.kf7mxe.inglenook.playback

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.Chapter
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.downloads.DownloadManager
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.serverScopedProperty
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlaybackState {
    // Current book being played
    val currentBook = Signal<Book?>(null)

    // Playback state
    val isPlaying = Signal(false)
    val isBuffering = Signal(false) // True while waiting for player to start
    val positionTicks = Signal(0L)
    val duration = Signal(0L)
    val playbackSpeed = Signal(1.0f)

    // Current chapter tracking
    val currentChapter = Signal<Chapter?>(null)

    // Sleep timer
    val sleepTimerMinutesRemaining = Signal<Int?>(null) // null = no timer, >0 = minutes remaining
    val sleepTimerMode = Signal<SleepTimerMode?>(null) // Tracks what mode was set
    private var sleepTimerJob: Job? = null

    // Skip amounts in ticks (10,000 ticks = 1ms)
    private const val SKIP_FORWARD_TICKS = 30 * 10_000_000L // 30 seconds
    private const val SKIP_BACKWARD_TICKS = 15 * 10_000_000L // 15 seconds
    private const val CHAPTER_RESTART_THRESHOLD_TICKS = 3 * 10_000_000L // 3 seconds
    private const val BUFFERING_CHECK_INTERVAL_MS = 500L
    private const val PROGRESS_SYNC_INTERVAL_MS = 2_000L
    private const val SLEEP_TIMER_COUNTDOWN_INTERVAL_MS = 60_000L

    // Progress sync job
    private var progressSyncJob: Job? = null

    // Platform audio player (expect/actual)
    private var audioPlayer: AudioPlayer? = null

    // Persisted last-played state (survives app restart)
    private val persistedLastBook get() = serverScopedProperty<Book?>("lastPlayedBook", null)
    private val persistedLastPosition get() = serverScopedProperty<Long>("lastPlayedPosition", 0L)

    // Per-book local position store (survives offline mode)
    private val persistedBookPositions get() = serverScopedProperty<Map<String, Long>>("bookPositions", emptyMap())

    /** Get the locally persisted playback position for a book. */
    fun getLocalPosition(bookId: String): Long {
        return persistedBookPositions.value[bookId] ?: 0L
    }

    private fun saveBookPosition(bookId: String, position: Long) {
        persistedBookPositions.value = persistedBookPositions.value + (bookId to position)
    }

    private fun saveLastPlayed() {
        val book = currentBook.value
        val position = positionTicks.value
        persistedLastBook.value = book
        persistedLastPosition.value = position
        // Also save to per-book position store
        if (book != null && position > 0L) {
            saveBookPosition(book.id, position)
        }
    }

    private fun clearLastPlayed() {
        persistedLastBook.value = null
        persistedLastPosition.value = 0L
    }

    suspend fun restoreLastPlayed() {
        println("DEBUG restoring lastPlayed")
        if (currentBook.value != null) return // Already have an active book
        val book = persistedLastBook.value ?: return
        currentBook.value = book
        println("DEBUG persistedLastPosition ${persistedLastPosition.value}")
        positionTicks.value = persistedLastPosition.value
        duration.value = book.duration
        isPlaying.value = false
        updateCurrentChapter()
    }

    suspend fun play(book: Book, startPosition: Long = 0L) {
        println("DEBUG playback play")
        currentBook.set(book)
        duration.value = book.duration
        positionTicks.value = startPosition
        isBuffering.value = true

        // Persist for restore on next app launch
        saveLastPlayed()

        // Update current chapter
        updateCurrentChapter()

        // Check if we have a local download for offline playback
        val localFilePath = DownloadManager.getLocalFilePath(book.id)

        // Start playback
        audioPlayer?.stop()
        audioPlayer = createAudioPlayer()
        audioPlayer?.play(book, startPosition, localFilePath)
        isPlaying.value = true

        // Start progress sync
        startProgressSync()

        // Report playback start to Jellyfin (if online)
        if (!ConnectivityState.offlineMode.value) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackStart(book.id, startPosition)
            }
        }
    }

    fun pause() {
        println("DEBUG PlayBackState pause")
        audioPlayer?.pause()
        isPlaying.value = false
        stopProgressSync()

        // Persist position so it survives app restart
        saveLastPlayed()

        // Report playback progress to Jellyfin
        val book = currentBook.value
        if (book != null && !ConnectivityState.offlineMode.value) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackProgress(book.id, positionTicks.value, true)
            }
        }
    }

    suspend fun resume() {
        println("DEBUG resumming ")
        if (audioPlayer == null) {
            val book = currentBook.value
            if (book != null) {
                play(book, positionTicks.value)
            }
            return
        }

        audioPlayer?.resume()
        isPlaying.value = true
        startProgressSync()

        // Report playback progress to Jellyfin
        val book = currentBook()
        if (book != null && !ConnectivityState.offlineMode.value) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackProgress(book.id, positionTicks.value, false)
            }
        }
    }

    suspend fun togglePlayPause() {
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
        isBuffering.value = false
        stopProgressSync()

        // Clear persisted state (user explicitly stopped)
        clearLastPlayed()

        // Report playback stopped to Jellyfin
        if (book != null && !ConnectivityState.offlineMode.value) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackStopped(book.id, positionTicks.value)
            }
        }

        currentBook.value = null
        positionTicks.value = 0L
        duration.value = 0L
        currentChapter.value = null
    }

    suspend fun seek(newPositionTicks: Long) {
        val clampedPosition = newPositionTicks.coerceIn(0L, duration.value)
        positionTicks.value = clampedPosition
        audioPlayer?.seek(clampedPosition)
        updateCurrentChapter()
    }

    suspend fun skipForward() {
        seek(positionTicks.value + SKIP_FORWARD_TICKS)
    }

    suspend fun skipBackward() {
        seek(positionTicks.value - SKIP_BACKWARD_TICKS)
    }

    suspend fun nextChapter() {
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

    suspend fun previousChapter() {
        val book = currentBook.value ?: return
        val chapters = book.chapters
        if (chapters.isEmpty()) return

        val current = currentChapter.value
        val currentIndex = chapters.indexOf(current)

        // If we're more than 3 seconds into the current chapter, restart it
        // Otherwise, go to the previous chapter
        val chapterStartTicks = current?.startPositionTicks ?: 0L
        val ticksIntoChapter = positionTicks.value - chapterStartTicks
        if (ticksIntoChapter > CHAPTER_RESTART_THRESHOLD_TICKS || currentIndex <= 0) {
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

    private suspend fun updateCurrentChapter() {
        val client = jellyfinClient()
        val book = currentBook.value ?: return
        val chapters = book.chapters.takeIf { it.isNotEmpty() || book.itemType == ItemType.Ebook }?:client?.getAudiobookChapters(book.id)?.map {
            Chapter(
                name = it.Name,
                startPositionTicks = it.StartPositionTicks,
                imageId = null
            )
        }?:emptyList()
        if (chapters.isEmpty()) {
            currentChapter.value = null
            return
        }

        val position = positionTicks.value
        val previousChapter = currentChapter.value
        val newChapter = chapters.lastOrNull { it.startPositionTicks <= position }
        currentChapter.value = newChapter

        // Check sleep timer for end of chapter mode
        checkEndOfChapterSleepTimer(previousChapter, newChapter)
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = AppScope.launch {
            // Wait for position to actually advance past the start point.
            // Checking pos > 0 alone is wrong for resumed books: the player reports
            // the seek position immediately even while still buffering.
            val startPos = positionTicks.value
            for (i in 0 until 10) {
                delay(BUFFERING_CHECK_INTERVAL_MS)
                val book = currentBook.value
                if (book != null && isPlaying.value) {
                    audioPlayer?.let { player ->
                        val pos = player.getCurrentPosition()
                        if (pos > startPos) {
                            positionTicks.value = pos
                            if (isBuffering.value) {
                                isBuffering.value = false
                            }
                        }
                    }
                }
                if (!isBuffering.value) break
            }
            isBuffering.value = false // Clear even if player didn't report position yet

            // Normal sync loop
            while (isActive) {
                delay(PROGRESS_SYNC_INTERVAL_MS)

                val book = currentBook.value
                if (book != null && isPlaying.value) {
                    // Update position from audio player
                    audioPlayer?.let {
                        positionTicks.value = it.getCurrentPosition()
                    }
                    updateCurrentChapter()

                    // Persist position for restore on next app launch
                    saveLastPlayed()

                    // Report to Jellyfin (if online)
                    if (!ConnectivityState.offlineMode.value) {
                        jellyfinClient.value?.reportPlaybackProgress(
                            book.id,
                            positionTicks.value,
                            !isPlaying.value
                        )
                    }
                }
            }
        }
    }

    private fun stopProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = null
    }

    // Update position (called by platform audio player)
    suspend fun onPositionUpdate(newPositionTicks: Long) {
        positionTicks.value = newPositionTicks
        updateCurrentChapter()
    }

    // Handle playback completion
    fun onPlaybackComplete() {
        val book = currentBook.value
        if (book != null && !ConnectivityState.offlineMode.value) {
            AppScope.launch {
                jellyfinClient.value?.reportPlaybackStopped(book.id, duration.value)
            }
        }
        stop()
    }

    // Sleep timer functions
    fun setSleepTimer(mode: SleepTimerMode) {
        cancelSleepTimer()
        sleepTimerMode.value = mode

        when (mode) {
            is SleepTimerMode.Minutes -> {
                sleepTimerMinutesRemaining.value = mode.minutes
                startSleepTimerCountdown()
            }
            SleepTimerMode.EndOfChapter -> {
                // Will be handled in chapter change detection
                sleepTimerMinutesRemaining.value = null
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerMinutesRemaining.value = null
        sleepTimerMode.value = null
    }

    private fun startSleepTimerCountdown() {
        sleepTimerJob?.cancel()
        sleepTimerJob = AppScope.launch {
            while (true) {
                delay(SLEEP_TIMER_COUNTDOWN_INTERVAL_MS)

                val remaining = sleepTimerMinutesRemaining.value
                if (remaining == null || remaining <= 0) {
                    break
                }

                if (isPlaying.value) {
                    val newRemaining = remaining - 1
                    sleepTimerMinutesRemaining.value = newRemaining

                    if (newRemaining <= 0) {
                        // Timer expired - pause playback
                        pause()
                        cancelSleepTimer()
                        break
                    }
                }
            }
        }
    }

    private fun checkEndOfChapterSleepTimer(previousChapter: Chapter?, newChapter: Chapter?) {
        val mode = sleepTimerMode.value
        if (mode == SleepTimerMode.EndOfChapter && previousChapter != null && newChapter != previousChapter) {
            // Chapter changed - pause playback
            pause()
            cancelSleepTimer()
        }
    }
}

// Sleep timer modes
sealed class SleepTimerMode {
    data class Minutes(val minutes: Int) : SleepTimerMode()
    data object EndOfChapter : SleepTimerMode()
}

// Platform-specific audio player interface
expect fun createAudioPlayer(): AudioPlayer

interface AudioPlayer {
    /**
     * Play the audiobook.
     * @param book The book to play
     * @param startPositionTicks Starting position in ticks
     * @param localFilePath Optional local file path for offline playback. If null, stream from server.
     */
    fun play(book: Book, startPositionTicks: Long, localFilePath: String? = null)
    fun pause()
    fun resume()
    fun stop()
    fun seek(positionTicks: Long)
    fun setPlaybackSpeed(speed: Float)
    fun getCurrentPosition(): Long
}
