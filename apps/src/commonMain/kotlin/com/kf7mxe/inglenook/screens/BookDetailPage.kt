package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.ChaptersList
import com.kf7mxe.inglenook.components.PlaybackControls
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.launch

@Routable("book/{bookId}")
class BookDetailPage(val bookId: String) : Page {
    override val title: Reactive<String> = Constant("Book")

    override fun ViewWriter.render() {
        val isLoading = Signal(true)
        val book = Signal<AudioBook?>(null)
        val errorMessage = Signal<String?>(null)
        val showChapters = Signal(false)

        // Load book details
        fun loadBook() {
            isLoading.value = true
            errorMessage.value = null
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        book.value = client.getBook(bookId)
                    }
                } catch (e: Exception) {
                    errorMessage.value = "Failed to load book: ${e.message}"
                } finally {
                    isLoading.value = false
                }
            }
        }

        // Initial load
        loadBook()

        col {
            gap = 0.rem

            // Scrollable content
            expanding.scrolls.col {
                padding = 1.rem
                gap = 1.5.rem

                // Loading state
                shownWhen { isLoading() }.centered.activityIndicator()

                // Error state
                shownWhen { errorMessage() != null && !isLoading() }.centered.col {
                    gap = 0.5.rem
                    text { ::content { errorMessage() ?: "" } }
                    button {
                        text("Retry")
                        onClick { loadBook() }
                    }
                }

                // Book content
                shownWhen { book() != null && !isLoading() }.col {
                    gap = 1.5.rem

                    // Book header with cover and info
                    row {
                        gap = 1.rem

                        // Cover image
                        sizedBox(SizeConstraints(width = 8.rem, height = 12.rem)).frame {
                            val currentBook = book.value
                            if (currentBook?.coverImageId != null) {
                                val client = jellyfinClient.value
                                image {
                                    source = ImageRemote(client?.getImageUrl(currentBook.coverImageId) ?: "")
                                    scaleType = ImageScaleType.Crop
                                }
                            } else {
                                centered.icon(Icon.book.copy(width = 4.rem, height = 4.rem), "Book cover")
                            }
                        }

                        // Book info
                        expanding.col {
                            gap = 0.25.rem

                            h2 { ::content { book()?.title ?: "" } }

                            text { ::content { book()?.authors?.joinToString(", ") ?: "Unknown Author" } }

                            if (book.value?.narrator != null) {
                                subtext { ::content { "Narrated by ${book()?.narrator ?: ""}" } }
                            }

                            // Series info
                            shownWhen { book()?.seriesName != null }.subtext {
                                ::content {
                                    val b = book()
                                    if (b?.indexNumber != null) {
                                        "${b.seriesName} #${b.indexNumber}"
                                    } else {
                                        b?.seriesName ?: ""
                                    }
                                }
                            }

                            // Duration
                            subtext {
                                ::content {
                                    val durationTicks = book()?.duration ?: 0L
                                    val totalSeconds = durationTicks / 10_000_000
                                    val hours = totalSeconds / 3600
                                    val minutes = (totalSeconds % 3600) / 60
                                    if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                                }
                            }

                            // Progress indicator
                            shownWhen { (book()?.userData?.playbackPositionTicks ?: 0L) > 0L }.col {
                                gap = 0.25.rem
                                progressBar {
                                    ::ratio {
                                        val b = book()
                                        val position = b?.userData?.playbackPositionTicks ?: 0L
                                        val dur = b?.duration ?: 1L
                                        if (dur > 0) (position.toFloat() / dur) else 0f
                                    }
                                }
                                subtext {
                                    ::content {
                                        val b = book()
                                        val position = b?.userData?.playbackPositionTicks ?: 0L
                                        val dur = b?.duration ?: 1L
                                        val percent = if (dur > 0) ((position.toFloat() / dur) * 100).toInt() else 0
                                        "$percent% complete"
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons
                    row {
                        gap = 0.5.rem

                        expanding.button {
                            row {
                                gap = 0.5.rem
                                centered.icon(Icon.playArrow, "Play")
                                centered.text {
                                    ::content {
                                        val position = book()?.userData?.playbackPositionTicks ?: 0L
                                        if (position > 0) "Continue" else "Play"
                                    }
                                }
                            }
                            onClick {
                                val currentBook = book.value
                                if (currentBook != null) {
                                    val startPosition = currentBook.userData?.playbackPositionTicks ?: 0L
                                    PlaybackState.play(currentBook, startPosition)
                                }
                            }
                            themeChoice += ImportantSemantic
                        }

                        button {
                            icon(Icon.download, "Download")
                            onClick {
                                // Navigate to downloads or start download
                                mainPageNavigator.navigate(DownloadsPage())
                            }
                        }
                    }

                    // Description section
                    shownWhen { book()?.description != null }.col {
                        gap = 0.5.rem
                        h3 { content = "Description" }
                        text { ::content { book()?.description ?: "" } }
                    }

                    // Chapters section
                    shownWhen { (book()?.chapters?.size ?: 0) > 0 }.col {
                        gap = 0.5.rem

                        button {
                            row {
                                gap = 0.5.rem
                                expanding.h3 {
                                    ::content { "Chapters (${book()?.chapters?.size ?: 0})" }
                                }
                                icon {
                                    ::source { if (showChapters()) Icon.unfoldLess else Icon.unfoldMore }
                                    description = "Toggle chapters"
                                }
                            }
                            onClick { showChapters.value = !showChapters.value }
                        }

                        shownWhen { showChapters() }.col {
                            val currentBook = book.value
                            if (currentBook != null && currentBook.chapters.isNotEmpty()) {
                                // Get current playback position for this book
                                val currentPosition = if (PlaybackState.currentBook.value?.id == currentBook.id) {
                                    PlaybackState.positionTicks.value
                                } else {
                                    currentBook.userData?.playbackPositionTicks ?: 0L
                                }

                                ChaptersList(
                                    chapters = currentBook.chapters,
                                    currentPositionTicks = currentPosition,
                                    onChapterClick = { chapter ->
                                        // Start playback from this chapter
                                        PlaybackState.play(currentBook, chapter.startPositionTicks)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Now playing mini controls (shown when playing this book)
            shownWhen { PlaybackState.currentBook()?.id == bookId && PlaybackState.currentBook() != null }.col {
                padding = 1.rem
                PlaybackControls(compact = true)
            }
        }
    }
}
