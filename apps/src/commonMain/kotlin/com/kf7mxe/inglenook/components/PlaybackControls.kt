package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.playback.PlaybackState

fun ViewWriter.PlaybackControls(compact: Boolean = false) {
    col {
        gap = if (compact) 0.5.rem else 1.rem

        // Progress bar with time
        col {
            gap = 0.25.rem

            progressBar {
                reactiveSuspending {
                    val position = PlaybackState.positionTicks()
                    val duration = PlaybackState.duration()
                    ratio = if (duration > 0) position.toFloat() / duration else 0f
                }
            }

            row {
                subtext {
                    reactiveSuspending {
                        content = formatDuration(PlaybackState.positionTicks())
                    }
                }
                expanding.space(1.0)
                subtext {
                    reactiveSuspending {
                        content = formatDuration(PlaybackState.duration())
                    }
                }
            }
        }

        // Control buttons
        centered.row {
            gap = if (compact) 0.5.rem else 1.rem

            // Previous chapter
            button {
                icon(Icon.skipPrevious, "Previous chapter")
                onClick { PlaybackState.previousChapter() }
            }

            // Skip back 15s
            button {
                icon(Icon.replay15, "Skip back 15 seconds")
                onClick { PlaybackState.skipBackward() }
            }

            // Play/Pause
            button {
                sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)) {
                    centered.icon {
                        reactiveSuspending {
                            source = if (PlaybackState.isPlaying()) Icon.pause else Icon.playArrow
                            description = if (PlaybackState.isPlaying()) "Pause" else "Play"
                        }
                    }
                }
                onClick { PlaybackState.togglePlayPause() }
                themeChoice = ImportantSemantic.onNext
            }

            // Skip forward 30s
            button {
                icon(Icon.forward30, "Skip forward 30 seconds")
                onClick { PlaybackState.skipForward() }
            }

            // Next chapter
            button {
                icon(Icon.skipNext, "Next chapter")
                onClick { PlaybackState.nextChapter() }
            }
        }

        // Additional controls (speed, sleep timer) - only in full mode
        if (!compact) {
            row {
                gap = 0.5.rem

                // Playback speed
                button {
                    row {
                        gap = 0.25.rem
                        icon(Icon.speed, "Speed")
                        text {
                            reactiveSuspending {
                                content = "${PlaybackState.playbackSpeed()}x"
                            }
                        }
                    }
                    onClick {
                        // Cycle through speeds: 0.5, 0.75, 1.0, 1.25, 1.5, 2.0
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        val currentIndex = speeds.indexOf(PlaybackState.playbackSpeed.value)
                        val nextIndex = (currentIndex + 1) % speeds.size
                        PlaybackState.setPlaybackSpeed(speeds[nextIndex])
                    }
                }

                expanding.space(1.0)

                // Sleep timer
                button {
                    row {
                        gap = 0.25.rem
                        icon(Icon.bedtime, "Sleep timer")
                        text("Sleep")
                    }
                    onClick {
                        // TODO: Show sleep timer dialog
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
