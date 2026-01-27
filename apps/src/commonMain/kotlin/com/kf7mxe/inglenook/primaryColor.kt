package com.kf7mxe.inglenook

import com.lightningkite.kiteui.models.*

// Cozy forest green primary color
val primaryColor = Color.fromHexString("#364a3b")

// Theme presets
object ThemeColors {
    val cozyGreen = Color.fromHexString("#364a3b")
    val oceanBlue = Color.fromHexString("#2c5f7c")
    val midnightDark = Color.fromHexString("#1a1a2e")
    val sunriseOrange = Color.fromHexString("#c67c4e")

    // Warm paper-like background colors
    val warmPaper = Color.fromHexString("#f5f0e8")
    val darkBackground = Color.fromHexString("#121212")
}

fun createTheme(preset: ThemePreset, customColor: Color? = null): Theme {
    return when (preset) {
        ThemePreset.Cozy -> Theme.flat2("cozy", Angle(0.35f)).copy(
            id = "cozy",
            background = ThemeColors.warmPaper,
            foreground = Color.fromHexString("#2d2d2d"),
            accent = ThemeColors.cozyGreen
        )
        ThemePreset.Ocean -> Theme.flat2("ocean", Angle(0.55f)).copy(
            id = "ocean",
            background = Color.fromHexString("#e8f1f5"),
            foreground = Color.fromHexString("#1a3a4a"),
            accent = ThemeColors.oceanBlue
        )
        ThemePreset.Midnight -> Theme.flat2("midnight", Angle(0.7f)).copy(
            id = "midnight",
            background = ThemeColors.darkBackground,
            foreground = Color.fromHexString("#e0e0e0"),
            accent = Color.fromHexString("#7c8aff")
        )
        ThemePreset.Sunrise -> Theme.flat2("sunrise", Angle(0.1f)).copy(
            id = "sunrise",
            background = Color.fromHexString("#fff8f0"),
            foreground = Color.fromHexString("#3d2c1e"),
            accent = ThemeColors.sunriseOrange
        )
        ThemePreset.Custom -> Theme.flat2("custom", Angle(0.35f)).copy(
            id = "custom",
            background = ThemeColors.warmPaper,
            foreground = Color.fromHexString("#2d2d2d"),
            accent = customColor ?: ThemeColors.cozyGreen
        )
    }
}

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
            cornerRadii = CornerRadii.ForceConstant(1.rem),
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
            cornerRadii = CornerRadii.ForceConstant(10.rem)
        ).withBack
    }
}

data object ChipSemantic : Semantic("chip-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "chip-semantic",
            cornerRadii = CornerRadii.ForceConstant(10.rem)
        ).withBack
    }
}

data object ToastSemantic : Semantic("toast-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "toast-semantic",
            cornerRadii = CornerRadii.ForceConstant(1.rem),
            background = theme.background.darken(1f),
            foreground = theme.background.lighten(1f),
            iconOverride = Color.green.darken(0.25f)
        ).withBack
    }
}

data object NowPlayingSemantic : Semantic("now-playing-semantic") {
    override fun default(theme: Theme): ThemeAndBack {
        return theme.copy(
            id = "now-playing",
            cornerRadii = CornerRadii.PerCorner(1.rem, topLeft = true, topRight = true, bottomLeft = false, bottomRight = false),
            elevation = 4.dp
        ).withBack
    }
}
