package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.Dimension
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon

/**
 * Reusable empty state display with icon, title, and description.
 */
fun ViewWriter.emptyState(
    icon: Icon,
    title: String,
    description: String,
    iconSize: Dimension = 3.rem
) {
    centered.col {
        icon(icon.copy(width = iconSize, height = iconSize), title)
        text(title)
        subtext(description)
    }
}
