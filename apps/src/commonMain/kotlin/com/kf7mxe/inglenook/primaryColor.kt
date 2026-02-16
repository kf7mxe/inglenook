package com.kf7mxe.inglenook

import com.lightningkite.kiteui.models.*

// Re-export the createTheme function from theming package
// Keep backward compatibility
val primaryColor = Color.fromHexString("#364a3b")

// Semantic definitions for the app
data object TopRoundedBottomSquaredSemantic: Semantic("top-rounded-bottom-squared-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme[TopRoundedBottomSquaredSemantic].theme.withBack(
            cornerRadii = CornerRadii.PerCorner(1.rem, topLeft = true, topRight = true, bottomLeft = false, bottomRight = false))
    }
}

data object SelectedAndRounded : Semantic("separator-semantic-rounded") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme[SelectedSemantic].theme.withBack(
            cornerRadii = CornerRadii.Fixed(1.rem),
        )
    }
}

data object SmallText : Semantic("small-text") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "small-text",
            cascading = false,
            theme.font.copy(size = 11.dp)
        ).withoutBack
    }
}

data object CircleSemantic : Semantic("circle-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "circle-semantic",
            cornerRadii = CornerRadii.Fixed(10.rem)
        ).withBack
    }
}

data object ChipSemantic : Semantic("chip-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "chip-semantic",
            cornerRadii = CornerRadii.Fixed(10.rem)
        ).withBack
    }
}

data object ToastSemantic : Semantic("toast-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "toast-semantic",
            cornerRadii = CornerRadii.Fixed(1.rem),
            background = theme.background.darken(1f),
            foreground = theme.background.lighten(1f),
            iconOverride = Color.green.darken(0.25f)
        ).withBack
    }
}


data object HiddenButtonSemantic : Semantic("hidden-btn") {
    override fun default(theme: Theme): ThemeAndBack = theme.withoutBack(
        semanticOverrides = SemanticOverrides(
            HoverSemantic.override { it.withoutBack },
            DownSemantic.override { it.withoutBack },
            ClickableSemantic.override { it.withoutBack },
        )
    )
}

data object BoldSemantic : Semantic("bold") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.withoutBack(
            font = theme.font.copy(bold = true)
        )
    }
}

data object BadgeSemantic : Semantic("badge") {
    override fun default(theme: Theme): ThemeAndBack {
        val back = if (theme.background.closestColor().perceivedBrightness > 0.5) Color.white else Color.black
        return theme.withBack(
            font = theme.font.copy(size = 0.6.rem),
            padding = Edges(0.25.rem),
            cornerRadii = CornerRadii.Fixed(9999.px),
            foreground = back.invert(),
            background = back.applyAlpha(0.9f),
        )
    }
}

data object GroupedInformationSemantic : Semantic("grp-info") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme[CardSemantic].theme.withBack(
            gap = 1.5.rem,
            padding = Edges(1.5.rem)
        )
    }
}

data object NotRelevantSemantic : Semantic("not-relevant") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.withoutBack(
            foreground = theme.foreground.closestColor().applyAlpha(0.5f)
        )
    }
}
