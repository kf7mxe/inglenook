package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.Chapter

fun ViewWriter.ChaptersList(
    chapters: List<Chapter>,
    currentPositionTicks: Long,
    onChapterClick: (Chapter) -> Unit
) {
    col {
        gap = 0.rem

        for ((index, chapter) in chapters.withIndex()) {
            val nextChapterStart = chapters.getOrNull(index + 1)?.startPositionTicks ?: Long.MAX_VALUE
            val isCurrent = currentPositionTicks >= chapter.startPositionTicks &&
                           currentPositionTicks < nextChapterStart

            button {
                row {
                    gap = 0.75.rem
                    padding = 0.5.rem

                    // Chapter number
                    centered.text {
                        content = "${index + 1}"
                        themeChoice = ThemeDerivation {
                            it.copy(foreground = it.foreground.applyAlpha(0.6f)).withoutBack
                        }.onNext
                    }

                    // Chapter name and duration
                    expanding.col {
                        gap = 0.rem
                        text {
                            content = chapter.name
                            ellipsis = true
                        }
                        subtext {
                            content = formatDuration(chapter.startPositionTicks)
                        }
                    }

                    // Current indicator
                    if (isCurrent) {
                        centered.icon(Icon.playArrow, "Currently playing")
                    }
                }
                onClick { onChapterClick(chapter) }

                if (isCurrent) {
                    themeChoice = SelectedSemantic.onNext
                }
            }

            // Separator between chapters
            if (index < chapters.size - 1) {
                separator()
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
