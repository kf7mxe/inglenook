package com.kf7mxe.inglenook.theming

import com.lightningkite.kiteui.models.*
import com.kf7mxe.inglenook.ThemePreset
import com.kf7mxe.inglenook.ThemeSettings
import com.kf7mxe.inglenook.ImportantSemanticSettings
import com.kf7mxe.inglenook.SelectedSemanticSettings
import com.kf7mxe.inglenook.CardSemanticSettings
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.storage.NowPlayingSemantic
import com.kf7mxe.inglenook.storage.UnSelectedTab
import com.lightningkite.kiteui.models.CornerRadii
import com.lightningkite.kiteui.models.CornerRadii.Fixed
import com.lightningkite.kiteui.models.Dimension
import com.lightningkite.kiteui.views.nav
import kotlin.math.abs
import kotlin.math.absoluteValue

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
        ThemePreset.AutumnCabin -> Theme.autumnCabin(primaryColor)
        ThemePreset.Midnight -> Theme.midnight(primaryColor)
        ThemePreset.Sunrise -> Theme.sunrise(primaryColor)
        ThemePreset.NeumorphismLight -> Theme.neomorphismLight(primaryColor ?: Color.fromHex(0xFF6200EE.toInt()))
        ThemePreset.NeumorphismDark -> Theme.neomorphismDark(primaryColor ?: Color.fromHex(0xFF6200EE.toInt()))
        ThemePreset.Hackerman -> Theme.hackerman(primaryColor)
        ThemePreset.Clouds -> Theme.clouds(primaryColor)
        ThemePreset.Obsidian -> Theme.obsidian(primaryColor)
        ThemePreset.Glassish -> Theme.glassish(settings)
        ThemePreset.Custom -> Theme.custom(settings)
    }
}

// Cozy theme - Forest green, warm background
fun Theme.Companion.cozy(accent: Color? = null): Theme {
    return Theme(
        id = "cozy-${(accent ?: Color.fromHexString("#7B8266")).toInt()}",
        font = FontAndStyle(),
        foreground =  Color.fromHexString("#3E2F28"),
        background = Color.fromHexString("#F0E2C6"),
        outline = accent?: Color.fromHexString("#7B8266"),
        outlineWidth = 2.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        semanticOverrides = SemanticOverrides(
            OuterSemantic.override {
                it.withBack(
                    padding = Edges.ZERO,
                    cascading = false,
                    outlineWidth = 0.dp
                )
            },
            CardSemantic.override {
                it.withBack(
                    background = it.background.closestColor().darken(0.05f),
                    outlineWidth = 1.dp,
                    outline = it.outline.closestColor().lighten(0.1f)
                )
            },
            BarSemantic.override { it.withBack(

                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true),
            ) },
            MainContentSemantic.override { it.withoutBack(
                cascading = false,
                padding = Edges(1.rem,0.rem,1.rem,0.rem),
                cornerRadii = CornerRadii.Fixed(0.rem),
                outlineWidth = 0.dp,
            )},
            ImportantSemantic.override {
                it.withBack(
                    background = it.outline,
                    foreground =Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    outlineWidth = 2.dp,
                )
            },
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    background = it.background.closestColor().lighten(0.05f)
                )
            },
            ImageSemantic.override {
                it.withBack(
                    cornerRadii =CornerRadii.Fixed(1.rem),
                    padding = Edges.ZERO,
                    outline = null,
                    outlineWidth = 0.dp
                )
            },

            DialogSemantic.override {
                it.withBack(
                    cornerRadii = CornerRadii.Fixed(1.rem),
                    padding = Edges(1.rem,1.rem,1.rem,1.rem)
                )
            },
            NavSemantic.override { it.withBack(
                cascading = false,
                gap=0.5.rem,
                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,false,false)) },
            UnselectedSemantic.override {
                it.withBack()
            },
            ButtonSemantic.override {
                it.copy(
                    id = "buttonSemantic",
                    outlineWidth = 0.dp
                ).withBack
            },

            ),
        cornerRadii = CornerRadii.RatioOfSpacing(1.5f)
    )
}

fun Theme.Companion.autumnCabin(accent: Color? = null): Theme {
    val primary = accent ?: Color.fromHexString("#D48441")
    val cornerRadii = CornerRadii.RatioOfSpacing(1.5f)
    return Theme(
        id = "autumn-cabin-${primary.toInt()}",
        font = FontAndStyle(),
        foreground = Color.fromHexString("#3E2F28"),
        background = Color.fromHexString("#E9DCC9"),
        outline = primary,
        outlineWidth = 2.px,
        elevation = 0.dp,
        gap = 0.75.rem,
        cornerRadii = cornerRadii,
        semanticOverrides = SemanticOverrides(
            CardSemantic.override {
                it.withBack(
                    background = it.background.closestColor().lighten(0.06f),
                    outlineWidth = 1.dp
                )
            },
            BarSemantic.override { it.withBack(

                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true),
            ) },
            NavSemantic.override { it.withBack(
                cascading = false,
                gap=0.5.rem,
                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,false,false)) },
            MainContentSemantic.override { it.withoutBack(
                cascading = false,
                padding = Edges(1.rem,0.rem,1.rem,0.rem),
                cornerRadii = CornerRadii.Fixed(0.rem),
                outlineWidth = 0.dp,
            )},            ImportantSemantic.override {
                it.withBack(
                    background = primary,
                    foreground = Color.white
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    outlineWidth = 2.dp,
                    outline = primary,
                )
            },
            FieldSemantic.override {
                it.withBack(outlineWidth = 1.dp)
            },
            ImageSemantic.override {
                it.withBack(
                    cornerRadii =cornerRadii,
                    padding = Edges.ZERO,
                    outline = null,
                    outlineWidth = 0.dp
                )
            },
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
                )
            },
            DialogSemantic.override {
                it.withBack(

                    cornerRadii =cornerRadii,
                    padding = Edges(1.rem,1.rem,1.rem,1.rem)
                )
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
        outlineWidth = 2.px,
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
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
                )
            },
            BarSemantic.override { it.withBack(

                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true),
            ) },
            NavSemantic.override { it.withBack(
                gap=0.5.rem,
                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,false,false)) },            MainContentSemantic.override { it.withBack(
                cascading = false,
                outlineWidth = 0.dp,
            )},
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
        outlineWidth = 2.px,
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
            OuterSemantic.override {
                it.withoutBack(

                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
                )
            },
            MainContentSemantic.override { it.withoutBack(
                outlineWidth = 0.dp
            )},
            BarSemantic.override { it.withBack(

                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true),
            ) },
            NavSemantic.override { it.withBack(
                cascading = false,
                gap=0.5.rem,
                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true)) },            MainContentSemantic.override { it.withBack },
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
fun Theme.Companion.neomorphismLight(accent: Color = Color.fromHex(0xFF6200EE.toInt())): Theme {
    return Theme.neumorphism(
    id = "neomorphism-${accent.toInt()}",
        baseColor =  Color.gray(0.9f),
        accentColor = accent,
        accentForeground =  if (accent.perceivedBrightness < 0.6f) Color.white else Color.black,
        lightShadowColor =  Color.white.applyAlpha(0.7f),
        darkShadowColor = Color.black.applyAlpha(0.15f),
        shadowDistance =  4.dp,
        shadowBlur = 8.dp,
        title = FontAndStyle(systemDefaultFont),
        body = FontAndStyle(systemDefaultFont),
        cornerRadii = CornerRadii.RatioOfSpacing(1f) ,
        gap = 1.rem,
    )
}

val lightShadowColor = Color.white.applyAlpha(0.08f)
val darkShadowColor = Color.black.applyAlpha(0.4f)
val shadowDistance: Dimension = 8.dp
val shadowBlur: Dimension = 16.dp
val convexShadows = Shadow.neumorphicConvex(
    distance = shadowDistance,
    blur = shadowBlur,
    lightColor = lightShadowColor,
    darkColor = darkShadowColor
)

fun Theme.Companion.neomorphismDark(accent: Color = Color.fromHex(0xFF6200EE.toInt())): Theme {
    return Theme.neumorphism(
        id = "neomorphism-dark-${accent.toInt()}",
        baseColor =  Color.gray(0.25f),
        accentColor = accent,
        accentForeground =  if (accent.perceivedBrightness < 0.6f) Color.white else Color.black,
        lightShadowColor = Color.white.applyAlpha(0.08f),
        darkShadowColor = Color.black.applyAlpha(0.4f),
        shadowDistance =  4.dp,
        shadowBlur = 8.dp,
        title = FontAndStyle(systemDefaultFont),
        body = FontAndStyle(systemDefaultFont),
        cornerRadii = CornerRadii.RatioOfSpacing(1f) ,
        gap = 1.rem,
        semanticOverrides = SemanticOverrides(
            NavSemantic.override {

                it.withBack(
                    cascading = false,
                    padding = Edges(1.rem),
//                    shadows =convexShadows
                    cornerRadii = CornerRadii.Fixed(1.rem) ,
                    shadows= convexShadows
                )
                },
            BarSemantic.override {
                it.withBack(
                    padding = Edges(1.rem),
//                    shadows =convexShadows
                    cornerRadii = CornerRadii.Fixed(1.rem) ,
                    shadows= convexShadows
                )
            }
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
        outlineWidth = 1.px,
        gap = 0.5.rem,
        padding = Edges(0.5.rem),
        cornerRadii = CornerRadii.Fixed(0.px),
        semanticOverrides = SemanticOverrides(
            BarSemantic.override { it.withBack(

                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true),
            ) },
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
                )
            },
            NavSemantic.override { it.withBack(
                gap=0.5.rem,
                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true)) },            MainContentSemantic.override { it.withBack(
                cascading = false,
                outlineWidth = 0.dp,
            )},
            MainContentSemantic.override { it.withoutBack },
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
        outlineWidth = 1.px,
        elevation = 0.px,
        cornerRadii = CornerRadii.Fixed(1.rem),
        semanticOverrides = SemanticOverrides(
            CardSemantic.override { it.withBack(elevation = 1.dp, background = Color.white) },
            BarSemantic.override { it[CardSemantic] },
            NavSemantic.override { it[CardSemantic] },
            MainContentSemantic.override { it.withoutBack },
            ImportantSemantic.override {
                val primaryFixed = primary.darken(0.3f)
                it.withBack(
                    background = primaryFixed,
                    foreground = if (primaryFixed.perceivedBrightness > 0.4f) Color.black else Color.white,
                )
            },
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
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
        outlineWidth = 2.px,
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
            BarSemantic.override { it.withBack(

                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,true,true),
            ) },
            NavSemantic.override { it.withBack(
                gap=0.5.rem,
                cornerRadii = CornerRadii.PerCorner(1.rem,true,true,false,false)) },            MainContentSemantic.override { it.withBack(
                cascading = false,
                outlineWidth = 0.dp,
            )},
            MainContentSemantic.override { it.withoutBack },
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
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
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


fun Theme.Companion.glassish(settings: ThemeSettings): Theme {
    val primaryColor = settings.primaryColor?.toColorOrNull() ?: Color.fromHexString("#6366f1")
    val backgroundColor = settings.secondaryColor?.toColorOrNull() ?: Color.fromHexString("#1a1a2e")
    val accentColor = settings.accentColor?.toColorOrNull() ?: primaryColor.darken(0.3f)
    val foreground = if (backgroundColor.perceivedBrightness <= 0.5) accentColor.lighten(0.5f) else accentColor.darken(0.5f)
    // Cap opacity so glass always has some transparency
//    val cappedBaseOpacity = settings.baseOpacity.coerceAtMost(0.85f)
    val baseOpacity = 0.40f

    // Use custom layout settings
    val cornerRadius = 5.rem
    val paddingValue = 0.75.rem
    val gapValue = 0.75.rem
    val elevationValue = 0.dp
    val outlineWidthValue = 2.dp

    return Theme(
        id = "glassish-${settings.hashCode()}",
        font = FontAndStyle(),
        foreground = foreground,
        background = backgroundColor.applyAlpha(baseOpacity),
        outline = accentColor.applyAlpha(settings.outlineOpacity),
        outlineWidth = outlineWidthValue,
        elevation = elevationValue,
        gap = gapValue,
        iconOverride = foreground,
        padding = Edges(paddingValue),
        cornerRadii = CornerRadii.AdaptiveToSpacing(cornerRadius),
        semanticOverrides = SemanticOverrides(
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
                )
            },
            CardSemantic.override { theme ->
                val cardOpacity = 0.20f
                val bg = (settings.cardSemanticSettings?.backgroundColor?.toColorOrNull()
                    ?: theme.background.closestColor().lighten(0.08f).applyAlpha(baseOpacity))
                    .let { if (cardOpacity < 1f) it.closestColor().applyAlpha(cardOpacity) else it }
                val ol = settings.cardSemanticSettings?.outlineColor?.toColorOrNull()
                    ?: accentColor.applyAlpha(settings.outlineOpacity)
                theme.withBack(background = bg, outlineWidth = 0.dp, outline = ol)
            },
            BarSemantic.override { it.withBack(
                cascading = false,
                cornerRadii = CornerRadii.PerCorner(1.rem,false,false,true,true),
            ) },
            NavSemantic.override {
                it.withBack(
                    cornerRadii = CornerRadii.PerCorner(1.rem,true,true,false,false),
                    background = it.background.closestColor().applyAlpha(0.9f),
                )
            },

            MainContentSemantic.override { it.withoutBack(
                cascading = false,
                padding = Edges(1.rem,0.rem,1.rem,0.rem),
                cornerRadii = CornerRadii.Fixed(0.rem),
                outlineWidth = 0.dp,
            ) },
            FieldSemantic.override {
                it.withBack(
                    background = it.background.closestColor().applyAlpha(0.6f),
                    outlineWidth = outlineWidthValue
                )
            },
            ImportantSemantic.override {
                val importantOpacity = 0.40f
                val bg = (settings.importantSemanticSettings?.backgroundColor?.toColorOrNull()
                    ?: primaryColor.applyAlpha(baseOpacity))
                    .let { if (importantOpacity < 1f) it.closestColor().applyAlpha(importantOpacity) else it }
                val fg = settings.importantSemanticSettings?.foregroundColor?.toColorOrNull()
                    ?: if (bg.closestColor().perceivedBrightness > 0.5f) Color.black else Color.white
                it.withBack(background = bg, foreground = fg)
            },
//            SelectedSemantic.override {
//                val selectedOpacity = 0.45f
//                val bg = (settings.selectedSemanticSettings?.backgroundColor?.toColorOrNull()
//                    ?: primaryColor.applyAlpha(0.3f * baseOpacity))
//                    .let { if (selectedOpacity < 1f) it.closestColor().applyAlpha(selectedOpacity) else it }
//                val ol = settings.selectedSemanticSettings?.outlineColor?.toColorOrNull() ?: primaryColor
//                it.withBack(background = bg, outlineWidth = 0.dp, outline = ol)
//            },
            HoverSemantic.override {
                it.withBack(background = it.background.lighten(0.15f))
            },
            DialogSemantic.override {
                it.withBack(
                    background = it.background.darken(1f).applyAlpha(0.9f),
                    outline = accentColor,
                    outlineWidth = outlineWidthValue
                )
            },
            ImageSemantic.override {
                it.withBack(
                    cornerRadii = CornerRadii(0.5.rem),
                    padding = Edges( 0.0.rem),
                    outlineWidth =  0.dp
                )
            },
            NowPlayingSemantic.override {
                it.withBack(
                    cascading = true,
                    background = it.outline.darken(5f).applyAlpha(0.99f),
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

    // Use custom layout settings
    val cornerRadius = settings.cornerRadius.toDouble().rem
    val paddingValue = settings.padding.toDouble().rem
    val gapValue = settings.gap.toDouble().rem
    val elevationValue = settings.elevation.toDouble().dp
    val outlineWidthValue = settings.outlineWidth.toDouble().dp

    return Theme(
        id = "custom-${settings.hashCode()}",
        font = FontAndStyle(),
        foreground = foreground,
        background = backgroundColor.applyAlpha(settings.baseOpacity),
        outline = accentColor.applyAlpha(settings.outlineOpacity),
        outlineWidth = outlineWidthValue,
        elevation = elevationValue,
        gap = gapValue,
        padding = Edges(paddingValue),
        cornerRadii = CornerRadii.AdaptiveToSpacing(cornerRadius),
        semanticOverrides = SemanticOverrides(
            OuterSemantic.override {
                it.withoutBack(
                    cascading = false,
                    padding = Edges.ZERO,
                    outlineWidth = 0.dp
                )
            },
            CardSemantic.override { theme ->
                val cardOpacity = settings.cardSemanticSettings?.opacity ?: 1f
                val bg = (settings.cardSemanticSettings?.backgroundColor?.toColorOrNull()
                    ?: theme.background.closestColor().lighten(0.08f).applyAlpha(settings.baseOpacity + settings.opacityStep))
                    .let { if (cardOpacity < 1f) it.closestColor().applyAlpha(cardOpacity) else it }
                val ol = settings.cardSemanticSettings?.outlineColor?.toColorOrNull()
                    ?: accentColor.applyAlpha(settings.outlineOpacity)
                val olw = settings.cardSemanticSettings?.outlineWidth?.toDouble()?.dp ?: outlineWidthValue
                theme.withBack(background = bg, outlineWidth = olw, outline = ol)
            },
            BarSemantic.override { it.withBack },
            NavSemantic.override {
                it.withBack(
                    background = it.background.closestColor().applyAlpha(0.9f),
                )
            },
            MainContentSemantic.override { it.withBack },
            FieldSemantic.override {
                it.withBack(
                    background = it.background.closestColor().applyAlpha(0.8f),
                    outlineWidth = outlineWidthValue
                )
            },
            ImportantSemantic.override {
                val importantOpacity = settings.importantSemanticSettings?.opacity ?: 1f
                val bg = (settings.importantSemanticSettings?.backgroundColor?.toColorOrNull() ?: primaryColor)
                    .let { if (importantOpacity < 1f) it.closestColor().applyAlpha(importantOpacity) else it }
                val fg = settings.importantSemanticSettings?.foregroundColor?.toColorOrNull()
                    ?: if (bg.closestColor().perceivedBrightness > 0.5f) Color.black else Color.white
                it.withBack(background = bg, foreground = fg)
            },
            SelectedSemantic.override {
                val selectedOpacity = settings.selectedSemanticSettings?.opacity ?: 1f
                val bg = (settings.selectedSemanticSettings?.backgroundColor?.toColorOrNull() ?: primaryColor.applyAlpha(0.3f))
                    .let { if (selectedOpacity < 1f) it.closestColor().applyAlpha(selectedOpacity) else it }
                val ol = settings.selectedSemanticSettings?.outlineColor?.toColorOrNull() ?: primaryColor
                val olw = settings.selectedSemanticSettings?.outlineWidth?.toDouble()?.dp ?: 2.dp
                it.withBack(background = bg, outlineWidth = olw, outline = ol)
            },
            HoverSemantic.override {
                it.withBack(background = it.background.lighten(0.15f))
            },
            DialogSemantic.override {
                it.withBack(
                    background = it.background.closestColor(),
                    outline = accentColor,
                    outlineWidth = outlineWidthValue
                )
            },
            ImageSemantic.override {
                it.withBack(
                    cornerRadii = CornerRadii(settings.imageSemanticSettings?.cornerRadius?.toDouble()?.rem ?: cornerRadius),
                    padding = Edges(settings.imageSemanticSettings?.padding?.toDouble()?.rem ?: 0.0.rem),
                    outlineWidth = settings.imageSemanticSettings?.outlineWidth?.toDouble()?.dp ?: 0.dp
                )
            }
        )
    )
}




fun Theme.Companion.flat(
    id: String,
    hue: Angle,
    accentHue: Angle = hue + Angle.halfTurn,
    saturation: Float = 0.7f,
    baseBrightness: Float = 0.1f,
    brightnessStep: Float = 0.05f,
    title: FontAndStyle = FontAndStyle(),
    body: FontAndStyle = FontAndStyle(),
) = Theme(
    id = id,
    font = body,
    elevation = 0.dp,
    cornerRadii = CornerRadii.RatioOfSpacing(0.8f),
    gap = 0.75.rem,
    outlineWidth = 0.px,
    foreground = if(baseBrightness > 0.6f) Color.black else Color.white,
    background = HSPColor(hue = hue, saturation = saturation, brightness = baseBrightness).toRGB(),
    outline = HSPColor(hue = hue, saturation = saturation, brightness = 0.4f).toRGB(),
    semanticOverrides = SemanticOverrides(
        HeaderSemantic.override {
            it.withoutBack(font = title)
        },
        ImportantSemantic.override {
            val existing = it.background.closestColor().toHSP()
            if(abs(existing.brightness - 0.5f) > brightnessStep * 3) {
                val b = existing.copy(brightness = 0.5f).toRGB()
                it.withBack(
                    foreground = b.highlight(1f),
                    background = b,
                    outline = b,
                )
            } else {
                val closerToAccent = (existing.hue angleTo hue).turns.absoluteValue > (existing.hue angleTo accentHue).turns.absoluteValue
                val b = HSPColor(hue = if(closerToAccent) hue else accentHue, saturation = saturation, brightness = 0.5f).toRGB()
                it.withBack(
                    foreground = b.highlight(1f),
                    background = b,
                    outline = b,
                )
            }
        },
        CardSemantic.override {
            it.withBack(
                background = it.background.closestColor().toHSP().let {
                    it.copy(brightness = it.brightness + brightnessStep)
                }.toRGB(),
                outline = it.outline.closestColor().toHSP().let {
                    it.copy(brightness = it.brightness + brightnessStep)
                }.toRGB()
            )
        },
        HoverSemantic.override {
            it.withBack(background = it.background.closestColor().toHSP().let {
                it.copy(brightness = it.brightness + brightnessStep)
            }.toRGB(), outline = it.outline.closestColor().toHSP().let {
                it.copy(brightness = it.brightness + brightnessStep)
            }.toRGB(), outlineWidth = it.outlineWidth * 2)
        },
        FocusSemantic.override {
            val o = it.outline.closestColor()
            val b = it.background.closestColor()
            if(b.alpha == 0f || abs(o.perceivedBrightness - b.perceivedBrightness) > 0.4) {
                it.withBack(
                    outlineWidth = it.outlineWidth + 3.dp,
                )
            } else {
                it.withBack(
                    outlineWidth = it.outlineWidth + 3.dp,
                    outline = Color.gray(baseBrightness).highlight(1f)
                )
            }
        },
        DownSemantic.override {
            it.withBack(background = it.background.closestColor().toHSP().let {
                it.copy(brightness = it.brightness + brightnessStep * 3)
            }.toRGB(), outline = it.outline.closestColor().toHSP().let {
                it.copy(brightness = it.brightness + brightnessStep * 3)
            }.toRGB(), outlineWidth = it.outlineWidth * 2)
        },

        FieldSemantic.override {
            it.withBack(
                outlineWidth = 1.px,
                background = it.background.closestColor(),
                cascading = false,
                cornerRadii = when(val base = it.cornerRadii) {
                    is CornerRadii.AdaptiveToSpacing -> CornerRadii.Fixed(base.value)
                    is CornerRadii.Fixed -> base
                    is CornerRadii.RatioOfSize -> base
                    is CornerRadii.RatioOfSpacing -> CornerRadii.Fixed(it.gap * base.value)
                    is CornerRadii.PerCorner -> base
                }
            )
        },
        BarSemantic.override { it.withoutBack },
        NavSemantic.override { it[CardSemantic] },
        OuterSemantic.override {
            it.withBack
        },
        DialogSemantic.override {
            it.withBack(
                background = it.background.closestColor(),
            )
        },
        MainContentSemantic.override {
            it.withBack(
                background = RadialGradient(
                    stops = listOf(
                        GradientStop(0f, HSPColor(hue = hue, saturation = saturation, brightness = baseBrightness + brightnessStep * 2).toRGB()),
                        GradientStop(0.4f, HSPColor(hue = hue, saturation = saturation, brightness = baseBrightness + brightnessStep).toRGB()),
                        GradientStop(1f, HSPColor(hue = hue, saturation = saturation, brightness = baseBrightness + brightnessStep).toRGB()),
                    ),
                )
            )
        },
        DialogSemantic.override {
            it.withBack(outlineWidth = 1.dp, gap = 2.rem, cascading = false)
        },
        ImageSemantic.override {
            it.withBack()
        }
    ),
)