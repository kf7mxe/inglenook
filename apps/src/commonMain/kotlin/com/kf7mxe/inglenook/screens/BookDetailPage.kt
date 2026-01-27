package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.applySafeInsets
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.ChaptersList
import com.kf7mxe.inglenook.components.PlaybackControls
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class BookDetailPage(val bookId: String) : Page {
    override val title: ReactiveContext.() -> String = { "" }

    override fun ViewWriter.render() {
        val book = Signal<AudioBook?>(null)
        val isLoading = Signal(true)
        val showChapters = Signal(false)

        // Load book details
        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    book.value = client.getBook(bookId)
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

        scrolls.col {
            padding = 1.rem
            gap = 1.rem

            shownWhen { isLoading() }.centered.activityIndicator()

            shownWhen { !isLoading() && book() == null }.centered.col {
                gap = 0.5.rem
                text("Book not found")
                button {
                    text("Go Back")
                    onClick { mainPageNavigator.goBack() }
                }
            }

            shownWhen { !isLoading() && book() != null }.col {
                gap = 1.5.rem

                // Book header with cover and info
                row {
                    gap = 1.rem

                    // Cover image
                    sizedBox(SizeConstraints(width = 8.rem, height = 12.rem)) {
                        image {
                            ::source {
                                val b = book()
                                if (b?.coverImageId != null) {
                                    val client = jellyfinClient.value
                                    ImageRemote(client?.getImageUrl(b.coverImageId) ?: "")
                                } else {
                                    ImageLocal(Icon.book)
                                }
                            }
                            scaleType = ImageScaleType.Crop
                        }
                    }

                    // Book info
                    expanding.col {
                        gap = 0.25.rem

                        h2 {
                            ::content { book()?.title ?: "" }
                        }

                        text {
                            ::content { book()?.authors?.joinToString(", ") ?: "Unknown Author" }
                            themeChoice = ThemeDerivation { it.copy(foreground = it.foreground.applyAlpha(0.8f)).withoutBack }.onNext
                        }

                        shownWhen { book()?.narrator != null }.subtext {
                            ::content { "Narrated by ${book()?.narrator ?: ""}" }
                        }

                        shownWhen { book()?.seriesName != null }.subtext {
                            ::content {
                                val b = book()
                                val seriesInfo = b?.seriesName ?: ""
                                val bookNum = b?.indexNumber
                                if (bookNum != null) "$seriesInfo #$bookNum" else seriesInfo
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
                    }
                }

                // Play button and controls
                row {
                    gap = 0.5.rem

                    expanding.button {
                        row {
                            gap = 0.5.rem
                            centered.icon(Icon.playArrow, "Play")
                            centered.text {
                                ::content {
                                    val progress = book()?.userData?.playbackPositionTicks ?: 0L
                                    if (progress > 0) "Resume" else "Play"
                                }
                            }
                        }
                        onClick {
                            val b = book.value
                            if (b != null) {
                                PlaybackState.play(b, b.userData?.playbackPositionTicks ?: 0L)
                            }
                        }
                        themeChoice = ImportantSemantic.onNext
                    }

                    button {
                        icon(Icon.download, "Download")
                        onClick {
                            // TODO: Implement download
                        }
                    }

                    button {
                        icon(Icon.collectionsBookmark, "Add to Bookshelf")
                        onClick {
                            // TODO: Show add to bookshelf dialog
                        }
                    }
                }

                // Progress bar if in progress
                shownWhen { (book()?.userData?.playbackPositionTicks ?: 0L) > 0L }.col {
                    gap = 0.25.rem
                    val progress = remember {
                        val position = book()?.userData?.playbackPositionTicks?.toFloat() ?: 0f
                        val duration = book()?.duration?.toFloat() ?: 1f
                        if (duration > 0) position / duration else 0f
                    }
                    progressBar {
                        ::ratio { progress() }
                    }
                    subtext {
                        ::content {
                            val position = book()?.userData?.playbackPositionTicks ?: 0L
                            val duration = book()?.duration ?: 0L
                            "${formatDuration(position)} / ${formatDuration(duration)}"
                        }
                    }
                }

                // Description
                shownWhen { !book()?.description.isNullOrBlank() }.col {
                    gap = 0.5.rem
                    h3 { content = "Description" }
                    text {
                        ::content { book()?.description ?: "" }
                    }
                }

                // Chapters
                shownWhen { book()?.chapters?.isNotEmpty() == true }.col {
                    gap = 0.5.rem

                    button {
                        row {
                            gap = 0.5.rem
                            expanding.h3 { content = "Chapters" }
                            icon {
                                ::source { if (showChapters()) Icon.expandLess else Icon.expandMore }
                            }
                        }
                        onClick { showChapters.value = !showChapters.value }
                    }

                    shownWhen { showChapters() }.col {
                        reactiveSuspending {
                            clearChildren()
                            val chapters = book()?.chapters ?: emptyList()
                            val currentPosition = book()?.userData?.playbackPositionTicks ?: 0L

                            for ((index, chapter) in chapters.withIndex()) {
                                button {
                                    row {
                                        gap = 0.5.rem
                                        text("${index + 1}.")
                                        expanding.col {
                                            gap = 0.rem
                                            text(chapter.name)
                                            subtext(formatDuration(chapter.startPositionTicks))
                                        }
                                        // Show indicator if current chapter
                                        val nextChapterStart = chapters.getOrNull(index + 1)?.startPositionTicks ?: Long.MAX_VALUE
                                        if (currentPosition >= chapter.startPositionTicks && currentPosition < nextChapterStart) {
                                            icon(Icon.playArrow, "Current")
                                        }
                                    }
                                    onClick {
                                        val b = book.value
                                        if (b != null) {
                                            PlaybackState.play(b, chapter.startPositionTicks)
                                        }
                                    }
                                }
                            }
                        }
                    }
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
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
