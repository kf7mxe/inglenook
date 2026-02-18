package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.SeekBarSemantic
import com.lightningkite.kiteui.views.atEnd
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.forEachById
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.ExperimentalTime

private const val SEEK_SYNC_THRESHOLD_MS = 50L

@OptIn(ExperimentalTime::class)
fun ViewWriter.PlaybackControls() {
    // Create a signal for the seek bar that syncs with PlaybackState
    val seekRatio = Signal(0f)

    // Track if we're currently seeking (to avoid feedback loops)
    var isUserDragging = false

    // Track the last time we updated seekRatio from PlaybackState
    var lastSyncTime = 0L

    // Sync seekRatio from PlaybackState when not user dragging
    PlaybackState.positionTicks.addListener {
        if (!isUserDragging) {
            val position = PlaybackState.positionTicks.value
            val duration = PlaybackState.duration.value
            lastSyncTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
            seekRatio.value = if (duration > 0) position.toFloat() / duration else 0f
        }
    }
    PlaybackState.duration.addListener {
        if (!isUserDragging) {
            val position = PlaybackState.positionTicks.value
            val duration = PlaybackState.duration.value
            lastSyncTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
            seekRatio.value = if (duration > 0) position.toFloat() / duration else 0f
        }
    }

    // When seekRatio changes from user input, seek in PlaybackState
    seekRatio.addListener {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (now - lastSyncTime > SEEK_SYNC_THRESHOLD_MS) {
            val newPosition = (seekRatio.value * PlaybackState.duration.value).toLong()
            launch {
                PlaybackState.seek(newPosition)
            }
        }
    }

    col {

        // Seek bar with time
        col {
            // Show loading indicator while buffering, seek bar when ready
            shownWhen { PlaybackState.isBuffering() }.centered.row {
                gap = 0.5.rem
                activityIndicator { }
                subtext { content = "Loading..." }
            }

            shownWhen { !PlaybackState.isBuffering() }.col {
                SeekBarSemantic.onNext.padded.slider {
                    min = 0f
                    max = 1f
                    value bind seekRatio
                }

                row {
                    subtext {
                        ::content { formatDuration(PlaybackState.positionTicks()) }
                    }
                    expanding.space(1.0)
                    subtext {
                        ::content { formatDuration(PlaybackState.duration()) }
                    }
                }
            }
        }

        // Chapter progress and time remaining (below seek bar)
        shownWhen { PlaybackState.currentBook() != null }.centered.row {
            gap = 0.5.rem

            subtext {
                ::content {
                    val book = PlaybackState.currentBook()
                    val chapters = book?.chapters ?: emptyList()
                    val currentChapter = PlaybackState.currentChapter()
                    if (chapters.isNotEmpty() && currentChapter != null) {
                        val currentIndex = chapters.indexOf(currentChapter)
                        val remaining = chapters.size - currentIndex - 1
                        "$remaining chapters left"
                    } else {
                        ""
                    }
                }
            }

            shownWhen { PlaybackState.currentBook()?.chapters?.isNotEmpty() == true }.subtext { content = " • " }

            subtext {
                ::content {
                    val position = PlaybackState.positionTicks()
                    val duration = PlaybackState.duration()
                    val remaining = duration - position
                    if (remaining > 0) {
                        val remainingSeconds = remaining / 10_000_000
                        val hours = remainingSeconds / 3600
                        val minutes = (remainingSeconds % 3600) / 60
                        if (hours > 0) {
                            "${hours}h ${minutes}m left"
                        } else {
                            "${minutes}m left"
                        }
                    } else {
                        ""
                    }
                }
            }
        }

        // Control buttons
        centered.row {

            // Previous chapter
            button {
                icon(Icon.skipPrevious, "Previous chapter")
                onClick { PlaybackState.previousChapter() }
            }

            // Skip back 15s
            button {
                icon(Icon.reverseThirtySeconds, "Skip back")
                onClick { PlaybackState.skipBackward() }
            }

            // Play/Pause
            button {
                sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)).centered.icon {
                    ::source { if (PlaybackState.isPlaying()) Icon.pause else Icon.playArrow }
                    ::description { if (PlaybackState.isPlaying()) "Pause" else "Play" }
                }
                onClick {
                    if (PlaybackState.isPlaying.value) {
                        PlaybackState.pause()
                    } else {
                        PlaybackState.resume()
                    }
                }
                themeChoice += ImportantSemantic
            }

            // Skip forward 30s
            button {
                icon(Icon.forwardThirtySeconds, "Skip forward")
                onClick { PlaybackState.skipForward() }
            }

            // Next chapter
            button {
                icon(Icon.skipNext, "Next chapter")
                onClick { PlaybackState.nextChapter() }
            }
        }

        // Bookmark button (only show when not compact and a book is playing)
        shownWhen { PlaybackState.currentBook() != null }.centered.button {
            row {
                gap = 0.5.rem
                icon(Icon.bookmark, "Add bookmark")
                text("Add Bookmark")
            }
            onClick {
                val book = PlaybackState.currentBook.value
                val position = PlaybackState.positionTicks.value
                val chapter = PlaybackState.currentChapter.value
                if (book != null) {
                    BookmarkRepository.createBookmark(
                        bookId = book.id,
                        positionTicks = position,
                        chapterName = chapter?.name
                    )
                }
            }
        }
    }
}

private fun formatDuration(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
