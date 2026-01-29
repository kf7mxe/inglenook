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
    val hue = when (preset) {
        ThemePreset.Cozy -> Angle(0.35f)
        ThemePreset.Ocean -> Angle(0.55f)
        ThemePreset.Midnight -> Angle(0.7f)
        ThemePreset.Sunrise -> Angle(0.1f)
        ThemePreset.Custom -> Angle(0.35f)
    }
    return Theme.flat2(preset.name.lowercase(), hue)
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
