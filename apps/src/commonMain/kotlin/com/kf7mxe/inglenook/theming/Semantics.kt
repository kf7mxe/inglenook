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


data object SeekBarSemantic : Semantic("seekbarSemantic") {
    override fun default(theme: Theme): ThemeAndBack = FieldSemantic.default(theme).theme.alter(
        background = if (theme.outline.closestColor().perceivedBrightness > 0.5f) theme.background.darken(0.5f) else theme.background.lighten(0.5f),
        foreground = theme.foreground
    ).withoutBack
}

data object NowPlayingSemantic : Semantic("nowPlayingSemantic") {
    override fun default(theme: Theme) =theme.copy(id="nowPlayingSemantic",
        cascading = false,
        cornerRadii = CornerRadii.PerCorner(1.rem, topLeft = true, topRight = true, bottomLeft = false, bottomRight = false),
//        padding = Edges.ZERO
        elevation = 4.dp

    ).withBack
}

data object SelectedTab : Semantic("selectedTab") {
    override fun default(theme: Theme) = ImportantSemantic.invoke(theme).theme .copy(id="selectedTab",
        cornerRadii = CornerRadii.Fixed(1.rem),
        padding = Edges(1.rem,0.25.rem),
        outline =  theme.outline,
        background = theme.outline,
    ).withBack
}

data object DangerSemantic : Semantic("danger") {
    override fun default(theme: Theme): ThemeAndBack = theme.copy(
        id = key,
        foreground = Color.red
    ).withoutBack
}

data object UnSelectedTab : Semantic("unselectedTab") {
    override fun default(theme: Theme) =theme.copy(id="unselectedTab",
//        cornerRadii = CornerRadii.Fixed(1.rem),
        padding = Edges(1.rem,0.25.rem),
        outline =  theme.background,
//        background = theme.outline,
    ).withBack
}