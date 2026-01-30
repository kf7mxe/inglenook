package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.playback.PlaybackState
import com.lightningkite.reactive.core.Signal

fun ViewWriter.PlaybackControls(compact: Boolean = false) {
    // Create a signal for the seek bar that syncs with PlaybackState
    val seekRatio = Signal(0f)

    // Track if we're currently seeking (to avoid feedback loops)
    var isSeeking = false

    // Sync seekRatio from PlaybackState when not seeking
    PlaybackState.positionTicks.addListener {
        if (!isSeeking) {
            val position = PlaybackState.positionTicks.value
            val duration = PlaybackState.duration.value
            seekRatio.value = if (duration > 0) position.toFloat() / duration else 0f
        }
    }
    PlaybackState.duration.addListener {
        if (!isSeeking) {
            val position = PlaybackState.positionTicks.value
            val duration = PlaybackState.duration.value
            seekRatio.value = if (duration > 0) position.toFloat() / duration else 0f
        }
    }

    // When seekRatio changes (from user interaction), seek in PlaybackState
    seekRatio.addListener {
        if (isSeeking) {
            val newPosition = (seekRatio.value * PlaybackState.duration.value).toLong()
            PlaybackState.seek(newPosition)
        }
    }

    col {
        gap = if (compact) 0.5.rem else 1.rem

        // Seek bar with time
        col {
            gap = 0.25.rem

            slider {
                min = 0f
                max = 1f
                value bind seekRatio
                // Note: The slider will update seekRatio when dragged
                // We use the isSeeking flag to distinguish user vs programmatic changes
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
                icon(Icon.reverseThirtySeconds, "Skip back")
                onClick { PlaybackState.skipBackward() }
            }

            // Play/Pause
            button {
                sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)).centered.icon {
                    ::source { if (PlaybackState.isPlaying()) Icon.pause else Icon.playArrow }
                    ::description { if (PlaybackState.isPlaying()) "Pause" else "Play" }
                }
                onClick { PlaybackState.togglePlayPause() }
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
