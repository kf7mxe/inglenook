package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningtime.theming.invertIfSimilarBrightness

fun Theme.Companion.hackerman(
    color: Color,
    secondary: Color = color.toHSP().let { it.copy(hue = it.hue + 0.5.turns) }.toRGB(),
): Theme {
//    println("Hackerman Primary Color: $color, Secondary Color: $secondary")
    return Theme(
        id = "hackermin-${color.toInt()}-${secondary.toInt()}",
        font = FontAndStyle(systemDefaultFixedWidthFont),
        background = Color.black.invertIfSimilarBrightness(color),
        foreground = color,
        outline = color,
        outlineWidth = 0.px,
        gap = 0.5.rem,
        padding = Edges(0.5.rem),
        cornerRadii = CornerRadii.Fixed(0.px),
        semanticOverrides = SemanticOverrides(
            BarSemantic.override { it.withBack },
            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            OuterSemantic.override {
                it.withBack(
                    cascading = false,
                    gap = 1.px,
                    padding = Edges.ZERO,
                    background = color
                )
            },
            FieldSemantic.override { it.withBack(outlineWidth = 1.px, outline = color) },
            CardSemantic.override { it.withBack(outlineWidth = 1.px, outline = color.darken(0.7f)) },
            MainContentSemantic.override { it.withBack },
            HoverSemantic.override { it.withBack(outlineWidth = 1.px, outline = color) },
            DownSemantic.override { it.withBack(outlineWidth = 1.px, outline = color) },
            SelectedSemantic.override { it.withBack(outlineWidth = 1.px, outline = color) },
            DialogSemantic.override { it.withBack(outlineWidth = 1.px, outline = color, cascading = false) },
            PopoverSemantic.override { it.withBack(outlineWidth = 1.px, outline = color, cascading = false) },
            UnselectedSemantic.override { it.withBack },

            ImportantSemantic.override {
                it.withBack(
                    outline = secondary,
                    outlineWidth = 1.px,
                    foreground = secondary,
                )
            },
            WorkingSemantic.override { it.withoutBack(foreground = FadingColor(color, color.darken(.2f))) },
            LoadingSemantic.override { it.withoutBack(foreground = FadingColor(color, color.darken(.2f))) },
        )
    )
}