package com.kf7mxe.inglenook.theming

import com.lightningkite.kiteui.models.*
import com.kf7mxe.inglenook.ThemePreset
import com.kf7mxe.inglenook.ThemeSettings

/**
 * Extension to convert hex string to Color, returning null if invalid
 */
fun String.toColorOrNull(): Color? = try {
    Color.fromHexString(this)
} catch (e: Exception) {
    null
}

/**
 * Creates a theme based on the preset and optional settings
 */
fun createTheme(preset: ThemePreset, settings: ThemeSettings = ThemeSettings()): Theme {
    val primaryColor = settings.primaryColor?.toColorOrNull()

    return when (preset) {
        ThemePreset.Cozy -> Theme.cozy(primaryColor)
        ThemePreset.Ocean -> Theme.ocean(primaryColor)
        ThemePreset.Midnight -> Theme.midnight(primaryColor)
        ThemePreset.Sunrise -> Theme.sunrise(primaryColor)
        ThemePreset.Material -> Theme.material(primaryColor)
        ThemePreset.Hackerman -> Theme.hackerman(primaryColor)
        ThemePreset.Clouds -> Theme.clouds(primaryColor)
        ThemePreset.Obsidian -> Theme.obsidian(primaryColor)
        ThemePreset.Custom -> Theme.custom(settings)
    }
}

// Cozy theme - Forest green, warm background
fun Theme.Companion.cozy(accent: Color? = null): Theme {
    val primary = accent ?: Color.fromHexString("#4a2b00")
    val baseHue = primary.toHSP().hue

    return Theme(
        id = "cozy-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.9f),
        background = HSPColor(hue = baseHue, saturation = 0.2f, brightness = 0.15f).toRGB(),
        outline = HSPColor(hue = baseHue, saturation = 0.3f, brightness = 0.3f).toRGB(),
        outlineWidth = 0.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = CornerRadii.RatioOfSpacing(0.5f),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override {
                it.withBack(
                    background = it.background.closestColor().lighten(0.08f),
                    outlineWidth = 1.dp,
                    outline = it.outline.closestColor().lighten(0.1f)
                )
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = if (primary.perceivedBrightness > 0.5f) Color.black else Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.3f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    background = it.background.closestColor().lighten(0.05f)
                )
            },
        )
    )
}

// Ocean theme - Blue tones
fun Theme.Companion.ocean(accent: Color? = null): Theme {
    val primary = accent ?: Color.fromHexString("#2c5f7c")

    return Theme(
        id = "ocean-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.9f),
        background = Color.fromHexString("#0d1b2a"),
        outline = Color.fromHexString("#1b3a4b"),
        outlineWidth = 0.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = CornerRadii.RatioOfSpacing(0.5f),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override {
                it.withBack(
                    background = it.background.closestColor().lighten(0.06f),
                    outlineWidth = 1.dp
                )
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.3f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
            FieldSemantic.override {
                it.withBack(outlineWidth = 1.dp)
            },
        )
    )
}

// Midnight theme - Dark theme
fun Theme.Companion.midnight(accent: Color? = null): Theme {
    val primary = accent ?: Color.fromHexString("#6366f1")

    return Theme(
        id = "midnight-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.9f),
        background = Color.fromHexString("#0f0f0f"),
        outline = Color.fromHexString("#1f1f1f"),
        outlineWidth = 0.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = CornerRadii.RatioOfSpacing(0.5f),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override {
                it.withBack(
                    background = Color.fromHexString("#1a1a1a"),
                    outlineWidth = 1.dp,
                    outline = Color.fromHexString("#2a2a2a")
                )
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.2f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    background = Color.fromHexString("#1a1a1a")
                )
            },
        )
    )
}

// Sunrise theme - Warm orange
fun Theme.Companion.sunrise(accent: Color? = null): Theme {
    val primary = accent ?: Color.fromHexString("#c67c4e")

    return Theme(
        id = "sunrise-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.15f),
        background = Color.fromHexString("#fef7ed"),
        outline = Color.fromHexString("#e5d5c5"),
        outlineWidth = 0.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = CornerRadii.RatioOfSpacing(0.5f),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override {
                it.withBack(
                    background = Color.white,
                    elevation = 1.dp
                )
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.15f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    background = Color.white
                )
            },
        )
    )
}

// Material theme - Material design style
fun Theme.Companion.material(accent: Color? = null): Theme {
    val primary = accent ?: HSPColor(hue = 0.7.turns, saturation = 0.8f, brightness = 0.7f).toRGB()

    return Theme(
        id = "material-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.2f),
        background = Color.gray(0.95f),
        outline = Color.gray(0.8f),
        separatorOverride = Color.gray(0.8f),
        outlineWidth = 0.px,
        elevation = 0.px,
        gap = 0.75.rem,
        padding = Edges(0.75.rem),
        cornerRadii = CornerRadii.AdaptiveToSpacing(0.75.rem),
        semanticOverrides = SemanticOverrides(
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    background = Color.white,
                    foreground = Color.gray(0.2f)
                )
            },
            CardSemantic.override { it.withBack(elevation = 1.dp, background = Color.white) },
            BarSemantic.override { it[ImportantSemantic] },
            NavSemantic.override { it.withBack(cornerRadii = CornerRadii.Fixed(0.px)) },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = if (primary.perceivedBrightness > 0.4f) Color.black else Color.white,
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.15f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
        )
    )
}

// Hackerman theme - Terminal/monochrome style
fun Theme.Companion.hackerman(accent: Color? = null): Theme {
    val primary = accent ?: Color.green
    val secondary = primary.toHSP().let { it.copy(hue = it.hue + 0.5.turns) }.toRGB()

    return Theme(
        id = "hackerman-${primary.toInt()}",
        font = FontAndStyle(systemDefaultFixedWidthFont),
        background = Color.black,
        foreground = primary,
        outline = primary,
        outlineWidth = 0.px,
        gap = 0.5.rem,
        padding = Edges(0.5.rem),
        cornerRadii = CornerRadii.Fixed(0.px),
        semanticOverrides = SemanticOverrides(
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            MainContentSemantic.override { it.withBack },
            FieldSemantic.override { it.withBack(outlineWidth = 1.px, outline = primary) },
            CardSemantic.override { it.withBack(outlineWidth = 1.px, outline = primary.darken(0.7f)) },
            HoverSemantic.override { it.withBack(outlineWidth = 1.px, outline = primary) },
            DownSemantic.override { it.withBack(outlineWidth = 1.px, outline = primary) },
            SelectedSemantic.override { it.withBack(outlineWidth = 2.px, outline = primary) },
            ImportantSemantic.override {
                it.withBack(
                    outline = secondary,
                    outlineWidth = 1.px,
                    foreground = secondary,
                )
            },
        )
    )
}

// Clouds theme - Soft rounded style
fun Theme.Companion.clouds(accent: Color? = null): Theme {
    val primary = accent ?: HSPColor(hue = 0.6.turns, saturation = 0.7f, brightness = 0.6f).toRGB()

    return Theme(
        id = "clouds-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.2f),
        background = Color.gray(0.95f),
        outlineWidth = 0.px,
        elevation = 0.px,
        cornerRadii = CornerRadii.Fixed(1.rem),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override { it.withBack(elevation = 1.dp, background = Color.white) },
            BarSemantic.override { it[CardSemantic] },
            NavSemantic.override { it[CardSemantic] },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                val primaryFixed = primary.darken(0.3f)
                it.withBack(
                    background = primaryFixed,
                    foreground = if (primaryFixed.perceivedBrightness > 0.4f) Color.black else Color.white,
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.2f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
        )
    )
}

// Obsidian theme - Dark with linear gradients
fun Theme.Companion.obsidian(accent: Color? = null): Theme {
    val primary = accent ?: HSPColor(hue = 0.8.turns, saturation = 0.6f, brightness = 0.6f).toRGB()

    return Theme(
        id = "obsidian-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.gray(0.85f),
        background = Color.fromHexString("#1e1e2e"),
        outline = Color.fromHexString("#313244"),
        outlineWidth = 0.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = CornerRadii.RatioOfSpacing(0.4f),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override {
                it.withBack(
                    background = Color.fromHexString("#282838"),
                    outlineWidth = 1.dp,
                    outline = Color.fromHexString("#3b3b4d")
                )
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            MainContentSemantic.override { it.withBack },
            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primary.applyAlpha(0.25f),
                    outlineWidth = 2.dp,
                    outline = primary
                )
            },
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    background = Color.fromHexString("#282838")
                )
            },
        )
    )
}

// Custom theme - Fully user customizable
fun Theme.Companion.custom(settings: ThemeSettings): Theme {
    val primaryColor = settings.primaryColor?.toColorOrNull() ?: Color.fromHexString("#6366f1")
    val backgroundColor = settings.secondaryColor?.toColorOrNull() ?: Color.fromHexString("#1a1a2e")
    val accentColor = settings.accentColor?.toColorOrNull() ?: primaryColor.darken(0.3f)
    val foreground = if (backgroundColor.perceivedBrightness <= 0.5) Color.white else Color.black

    return Theme(
        id = "custom-${settings.hashCode()}",
        font = FontAndStyle(),
        foreground = foreground,
        background = backgroundColor.applyAlpha(settings.baseOpacity),
        outline = accentColor.applyAlpha(settings.outlineOpacity),
        outlineWidth = 0.dp,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = CornerRadii.AdaptiveToSpacing(0.5.rem),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override { theme ->
                val newBack = theme.background.closestColor().lighten(0.08f).applyAlpha(settings.baseOpacity + settings.opacityStep)
                theme.withBack(
                    background = newBack,
                    outlineWidth = 1.dp,
                    outline = accentColor.applyAlpha(settings.outlineOpacity)
                )
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override {
                it.withBack(
                    background = it.background.closestColor().applyAlpha(0.9f)
                )
            },
            MainContentSemantic.override { it.withBack },
            FieldSemantic.override {
                it.withBack(
                    background = it.background.closestColor().applyAlpha(0.8f),
                    outlineWidth = 1.px
                )
            },
            ImportantSemantic.override {
                it.withBack(
                    background = primaryColor,
                    foreground = if (primaryColor.perceivedBrightness > 0.5f) Color.black else Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = primaryColor.applyAlpha(0.3f),
                    outlineWidth = 2.dp,
                    outline = primaryColor
                )
            },
            HoverSemantic.override {
                it.withBack(background = it.background.lighten(0.15f))
            },
            DialogSemantic.override {
                it.withBack(
                    background = it.background.closestColor(),
                    outline = accentColor,
                    outlineWidth = 1.dp
                )
            },
        )
    )
}
