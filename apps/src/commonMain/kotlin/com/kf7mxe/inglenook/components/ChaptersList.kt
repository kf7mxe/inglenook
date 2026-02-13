package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.Chapter
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember

/**
 * ChaptersList for displaying audiobook chapters
 */
fun ViewWriter.chaptersList(
    chapters: Reactive<List<Chapter>>,
    onTapChapter: suspend (chapter: Chapter) -> Unit
) {
    scrolling.col {

        // Get chapters once for rendering
        forEachUpdating(chapters) { chapter ->
            val index = remember {

                chapters().indexOf(chapter())
            }
            val currentIndex = remember {
                println("DEBUG index() ${index()}")
                println("DEBUG PlaybackState.currentChapter() ${PlaybackState.currentChapter() != null}")
                if (chapters().indexOf(PlaybackState.currentChapter()) == index()) index() else -1
            }


            val isCurrent = remember {
                println("DEBUG index ${index()}")
                println("DEBUG currentIndex() ${currentIndex()}")
                index() == currentIndex()
            }
            val isPast = remember {
                index() < currentIndex()
            }

            button {
                row {
                    gap = 0.5.rem
                    padding = 0.5.rem

                    // Chapter number
                    centered.subtext {
                        ::content {
                            "${index() + 1}"
                        }
                    }

                    // Chapter name
                    expanding.col {
                        gap = 0.rem
                        text {
                            ::content {
                                chapter().name
                            }
                            ellipsis = true
                        }
                        // Show start time
                        subtext {

                            ::content {
                                val totalSeconds = chapter().startPositionTicks / 10_000_000
                                val hours = totalSeconds / 3600
                                val minutes = (totalSeconds % 3600) / 60
                                val seconds = totalSeconds % 60
                                if (hours > 0) {
                                    "$hours:${
                                        minutes.toString().padStart(2, '0')
                                    }:${seconds.toString().padStart(2, '0')}"
                                } else {
                                    "$minutes:${seconds.toString().padStart(2, '0')}"
                                }
                            }
                        }
                    }

                    // Current indicator
                    icon {
                        ::shown {
                            isCurrent()
                        }
                        source = Icon.playArrow
                    }

                }

                dynamicTheme {
                    when {
                        isCurrent() -> SelectedSemantic

                        isPast() -> ThemeDerivation {
                            it.copy(
                                id = "past-chapter",
                                foreground = it.foreground.closestColor().applyAlpha(0.6f)
                            ).withBack

                        }

                        else -> null
                    }
                }

                onClick {
                    onTapChapter(chapter())
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
