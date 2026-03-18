package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.util.formatDuration
import com.kf7mxe.inglenook.util.formatDurationShort
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.kf7mxe.inglenook.storage.SeekBarSemantic
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.important
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

private const val SEEK_SYNC_THRESHOLD_MS = 50L

@OptIn(ExperimentalTime::class)
fun ViewWriter.playbackControls() {
    // Create a signal for the seek bar that syncs with PlaybackState
    val seekRatio =
        Signal(if (PlaybackState.duration.value > 0) PlaybackState.positionTicks.value.toFloat() / PlaybackState.duration.value else 0f)

    // Track if we're currently seeking (to avoid feedback loops)
    var isUserDragging = false

    // Track the last time we updated seekRatio from PlaybackState
    var lastSyncTime = 0L

    // Sync seekRatio from PlaybackState when not user dragging
    PlaybackState.positionTicks.addListener {
        println("DEBUG position ticks listeners")
        if (!isUserDragging) {
            val position = PlaybackState.positionTicks.value
            val duration = PlaybackState.duration.value
            lastSyncTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
            println("DEBUG duration = $duration")
            println("DEBUG positionTicks = $position")
            println("DEBUG value = ${if (duration > 0) position.toFloat() / duration else 0f}")
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
        launch {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            if (now - lastSyncTime > SEEK_SYNC_THRESHOLD_MS) {
                val newPosition = (seekRatio.value * PlaybackState.duration.value).toLong()
                PlaybackState.seek(newPosition)
            }
        }
    }

    val chapters = remember {
        val book = PlaybackState.currentBook()
        book?.chapters ?: emptyList()
    }

    col {

        // Seek bar with time
        col {
            // Show loading indicator while buffering, seek bar when ready
            shownWhen { PlaybackState.isBuffering() }.centered.row {
                gap = 0.5.rem
                sizeConstraints(height = 1.5.rem, width = 1.5.rem).inglenookActivityIndicator()
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

                    val currentChapter = PlaybackState.currentChapter()

                    val chapters = chapters()
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
                        "${formatDurationShort(remaining)} left"
                    } else {
                        ""
                    }
                }
            }
        }

        // Control buttons
        centered.row {

            // Previous chapter
            buttonTheme.button {
                ::shown{
                    chapters().isNotEmpty()
                }
                icon(Icon.skipPrevious, "Previous chapter")
                onClick { PlaybackState.previousChapter() }
                onLongClick {
                    openPopover(PopoverPreferredDirection.aboveLeft, {
                        text("Previous chapter")
                    })
                }
            }

            // Skip back 15s
            buttonTheme.button {
                icon(Icon.reverseThirtySeconds, "Skip back")
                onClick { PlaybackState.skipBackward() }
                onLongClick {
                    openPopover(PopoverPreferredDirection.aboveLeft, {
                        text("Go back 30 seconds")
                    })
                }
            }

            // Play/Pause
            important.buttonTheme.button {
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
            }

            // Skip forward 30s
            buttonTheme.button {
                icon(Icon.forwardThirtySeconds, "Skip forward")
                onClick { PlaybackState.skipForward() }
                onLongClick {
                    openPopover(PopoverPreferredDirection.aboveLeft, {
                        text("Go forward 30 seconds")
                    })
                }
            }

            // Next chapter
            buttonTheme.button {
                ::shown{
                    chapters().isNotEmpty()
                }
                icon(Icon.skipNext, "Next chapter")
                onClick { PlaybackState.nextChapter() }
                onLongClick {
                    openPopover(PopoverPreferredDirection.aboveLeft, {
                        text("Next chapter")
                    })
                }
            }
        }


        // Bookmark buttons (only show when a book is playing)
        shownWhen { PlaybackState.currentBook() != null }.centered.row {
            // Add Bookmark button - opens note dialog
            centered.expanding.button {
                row {
                    icon(Icon.addBookmark, "Add bookmark")
                    text("Add Bookmark")
                }
                onClick {
                    val book = PlaybackState.currentBook.value
                    val position = PlaybackState.positionTicks.value
                    val chapter = PlaybackState.currentChapter.value
                    if (book != null) {
                        val noteInput = Signal("")
                        coordinatorFrame?.bottomSheet(
                            partialRatio = 0.5f,
                            startState = BottomSheetState.PARTIALLY_EXPANDED
                        ) { control ->
                            card.col {
                                h3 { content = "Add Bookmark" }
                                subtext {
                                    content = "Position: ${formatDuration(position)}"
                                }
                                fieldTheme.textInput {
                                    hint = "Add a note (optional)..."
                                    keyboardHints = KeyboardHints(KeyboardCase.Sentences, KeyboardType.Text)
                                    content bind noteInput
                                }
                                row {
                                    expanding.button {
                                        centered.text("Cancel")
                                        onClick { control.close() }
                                    }
                                    expanding.button {
                                        centered.text("Save")
                                        onClick {
                                            BookmarkRepository.createBookmark(
                                                bookId = book.id,
                                                positionTicks = position,
                                                note = noteInput.value.takeIf { it.isNotBlank() },
                                                chapterName = chapter?.name
                                            )
                                            control.close()
                                        }
                                        themeChoice += ImportantSemantic
                                    }
                                }
                            }
                        }
                    }
                }
            }
            centered.separator()

            // View Bookmarks button - opens bookmarks list
            centered.expanding.button {
                row {
                    icon(Icon.bookmark, "View bookmarks")
                    text("Bookmarks")
                }
                onClick {
                    val book = PlaybackState.currentBook.value
                    if (book != null) {
                        val bookmarkSignal = Signal(BookmarkRepository.getBookmarksForBook(book.id))
                        coordinatorFrame?.bottomSheet(
                            partialRatio = 0.75f,
                            startState = BottomSheetState.PARTIALLY_EXPANDED
                        ) { control ->
                            card.col {
                                row {
                                    expanding.h3 { content = "Bookmarks" }
                                    button {
                                        icon(Icon.close, "Close")
                                        onClick { control.close() }
                                    }
                                }
                                bookmarksList(book.id, bookmarkSignal, { bookmark ->
                                    PlaybackState.seek(bookmark)
                                    control.close()
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

