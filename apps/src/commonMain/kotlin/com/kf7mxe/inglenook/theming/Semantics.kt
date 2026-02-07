package com.kf7mxe.inglenook.storage

import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.Semantic
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeAndBack
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.Fixed
import com.lightningkite.kiteui.views.ViewModifierDsl3
import com.lightningkite.kiteui.views.ViewWriter
import kotlin.time.Duration

data object BoldSemantic : Semantic("bld") {
    override fun default(theme: Theme): ThemeAndBack = theme.copy(
        id = key,
        font = theme.font.copy(bold = true)
    ).withoutBack
}

data class Background(val pad: Boolean = false, val background: (Theme) -> Paint) : Semantic("mnbck") {
    override fun default(theme: Theme): ThemeAndBack =
        theme.copy(
            id = key,
            background = background(theme)
        ).let {
            if (pad) it.withBack else it.withBackNoPadding
        }
}

data object BadgeSemantic : Semantic("badge") {
    override fun default(theme: Theme): ThemeAndBack = WarningSemantic.default(theme).theme.alter(
        cornerRadii = CornerRadii.Fixed(10.rem),
    ).withBackNoPadding
}

data object ImageSemantic : Semantic("imageSemantic") {
    override fun default(theme: Theme): ThemeAndBack = theme.copy(id="imageSemantic",
        cornerRadii = Fixed(0.rem),
//        padding = Edges.ZERO
        ).withBackNoPadding
}