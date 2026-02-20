package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.coordinatorFrame
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.forEach
import com.kf7mxe.inglenook.Bookmark
import com.kf7mxe.inglenook.edit
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.storage.BookmarkRepository
import com.lightningkite.kiteui.views.card
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Reactive

/**
 * Reusable bookmarks list component for both PlaybackControls (bottomSheet) and BookDetailPage.
 * Shows bookmarks sorted by position, with seek, edit note, and delete actions.
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
fun ViewWriter.bookmarksList(
    bookId: String,
    bookmarks: Signal<List<Bookmark>>,
    onSeek: (suspend (positionTicks: Long) -> Unit)? = null
) {
    col {
        gap = 0.rem

        shownWhen { bookmarks().isEmpty() }.centered.col {
            padding = 1.rem
            subtext("No bookmarks yet")
        }

        forEach(bookmarks) { bookmark ->
            row {
                padding = 0.5.rem
                gap = 0.5.rem

                // Tap to seek to bookmark position
                expanding.button {
                    col {
                        gap = 0.25.rem
                        // Note as primary text if present, otherwise show timestamp
                        bookmark.note?.let { noteText ->
                            text {
                                content = noteText
                                ellipsis = true
                            }
                        }
                        // Timestamp
                        val totalSeconds = bookmark.positionTicks / 10_000_000
                        val hours = totalSeconds / 3600
                        val minutes = (totalSeconds % 3600) / 60
                        val seconds = totalSeconds % 60
                        val timeStr = if (hours > 0) {
                            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
                        } else {
                            "$minutes:${seconds.toString().padStart(2, '0')}"
                        }
                        if (bookmark.note != null) {
                            subtext { content = timeStr }
                        } else {
                            text { content = timeStr }
                        }

                        bookmark.chapterName?.let { chapterName ->
                            subtext {
                                content = chapterName
                                ellipsis = true
                            }
                        }
                    }

                    onClick {
                        if (onSeek != null) {
                            onSeek(bookmark.positionTicks)
                        } else {
                            // Default: use PlaybackState
                            PlaybackState.seek(bookmark.positionTicks)
                        }
                    }
                }

                // Edit note button
                button {
                    icon(Icon.edit, "Edit note")
                    onClick {
                        val noteInput = Signal(bookmark.note ?: "")
                        coordinatorFrame?.bottomSheet(
                            partialRatio = 0.5f,
                            startState = BottomSheetState.PARTIALLY_EXPANDED
                        ) { control ->
                            card.col {
                                padding = 1.rem
                                gap = 0.75.rem
                                h3 { content = "Edit Bookmark Note" }
                                fieldTheme.textInput {
                                    hint = "Add a note..."
                                    keyboardHints = KeyboardHints(KeyboardCase.Sentences, KeyboardType.Text)
                                    content bind noteInput
                                }
                                row {
                                    gap = 0.5.rem
                                    expanding.button {
                                        text("Cancel")
                                        onClick { control.close() }
                                    }
                                    expanding.button {
                                        text("Save")
                                        onClick {
                                            val newNote = noteInput.value.takeIf { it.isNotBlank() }
                                            BookmarkRepository.updateBookmark(
                                                bookmark.copy(note = newNote)
                                            )
                                            bookmarks.value = BookmarkRepository.getBookmarksForBook(bookId)
                                            control.close()
                                        }
                                        themeChoice += ImportantSemantic
                                    }
                                }
                            }
                        }
                    }
                }

                // Delete button
                button {
                    icon(Icon.delete, "Delete bookmark")
                    onClick {
                        BookmarkRepository.deleteBookmark(bookmark._id)
                        bookmarks.value = BookmarkRepository.getBookmarksForBook(bookId)
                    }
                    themeChoice += ThemeDerivation { it.copy(id = "danger", foreground = Color.red).withoutBack }
                }
            }
            separator()
        }
    }
}
