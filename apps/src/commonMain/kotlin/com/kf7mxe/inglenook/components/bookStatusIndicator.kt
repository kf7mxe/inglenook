package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.checkCircle
import com.kf7mxe.inglenook.util.calculateProgressPercent
import com.kf7mxe.inglenook.util.calculateProgressRatio
import com.kf7mxe.inglenook.util.formatDurationShort
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.Reactive

/**
 * Displays book completion status: completed checkmark, in-progress percentage + progress bar,
 * or duration for not-started audiobooks.
 */
fun ViewWriter.bookStatusIndicator(book: Reactive<Book>) {
    // Completed
    shownWhen { book().userData?.played == true }.row {
        icon(Icon.checkCircle, "Completed")
        subtext { content = "Completed" }
    }
    // In progress
    shownWhen {
        val b = book()
        (b.userData?.playbackPositionTicks ?: 0L) > 0L && b.userData?.played != true
    }.row {
        subtext {
            ::content {
                val b = book()
                val position = b.userData?.playbackPositionTicks ?: 0L
                "${calculateProgressPercent(position, b.duration)}%"
            }
        }
        expanding.progressBar {
            ::ratio {
                val b = book()
                val position = b.userData?.playbackPositionTicks ?: 0L
                calculateProgressRatio(position, b.duration)
            }
        }
    }
    // Not started - show duration (audiobooks only)
    shownWhen {
        val b = book()
        (b.userData?.playbackPositionTicks ?: 0L) == 0L && b.userData?.played != true && b.itemType == ItemType.AudioBook
    }.subtext {
        ::content { formatDurationShort(book().duration) }
    }
}
