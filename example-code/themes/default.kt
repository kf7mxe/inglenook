package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningtime.Resources
import kotlin.math.abs
import kotlin.math.absoluteValue

fun Theme.Companion.default(color: Color?): Theme {
    val baseColor = Color.fromHex(0x2D3237).toHSP()

    val hue: Angle = baseColor.hue
    val accentHue: Angle = hue + Angle.halfTurn
    val saturation: Float = baseColor.saturation
    val baseBrightness: Float = baseColor.brightness - 0.1f
    val brightnessStep: Float = 0.05f
    val title: FontAndStyle = FontAndStyle(Resources.livvic, weight = 600, allCaps = true)
    val body: FontAndStyle = FontAndStyle(Resources.livvic)
    return Theme(
        id = "default-${color?.toInt()}",
        font = body,
        elevation = 0.dp,
        cornerRadii = CornerRadii.RatioOfSpacing(0.8f),
        gap = 0.75.rem,
        outlineWidth = 0.px,
        foreground = if (baseBrightness > 0.6f) Color.black else Color.white,
        iconOverride = color,
        background = HSPColor(hue = hue, saturation = saturation, brightness = baseBrightness).toRGB(),
        outline = HSPColor(hue = hue, saturation = saturation, brightness = 0.4f).toRGB(),
        semanticOverrides = SemanticOverrides(
            HeaderSemantic.override {
                it.withoutBack(font = title)
            },
            ImportantSemantic.override {
                val existing = it.background.closestColor().toHSP()
                if (abs(existing.brightness - 0.5f) > brightnessStep * 3) {
                    val b = existing.copy(brightness = 0.3f).toRGB()
                    it.copy(
                        id = "imp",
                        foreground = b.highlight(1f),
                        background = b,
                        outline = b,
                    )
                } else {
                    val closerToAccent =
                        (existing.hue angleTo hue).turns.absoluteValue > (existing.hue angleTo accentHue).turns.absoluteValue
                    val b = HSPColor(
                        hue = if (closerToAccent) hue else accentHue,
                        saturation = saturation,
                        brightness = 0.5f
                    ).toRGB()
                    it.copy(
                        id = "imp",
                        foreground = b.highlight(1f),
                        background = b,
                        outline = b,
                    )
                }.withBack
            },
            CardSemantic.override {
                it.copy(
                    id = "crd",
                    background = it.background.closestColor().toHSP().let {
                        it.copy(brightness = it.brightness + brightnessStep)
                    }.toRGB(),
                    outline = it.outline.closestColor().toHSP().let {
                        it.copy(brightness = it.brightness + brightnessStep)
                    }.toRGB()
                ).withBack
            },
            HoverSemantic.override {
                it.copy(id = "hov", background = it.background.closestColor().toHSP().let {
                    it.copy(brightness = it.brightness + brightnessStep)
                }.toRGB(), outline = it.outline.closestColor().toHSP().let {
                    it.copy(brightness = it.brightness + brightnessStep)
                }.toRGB(), outlineWidth = it.outlineWidth * 2).withBack
            },
            FocusSemantic.override {
                val o = it.outline.closestColor()
                val b = it.background.closestColor()
                if (b.alpha == 0f || abs(o.perceivedBrightness - b.perceivedBrightness) > 0.4) {
                    it.copy(
                        id = "fcs",
                        outlineWidth = it.outlineWidth + 3.dp,
                    )
                } else {
                    it.copy(
                        id = "fcs",
                        outlineWidth = it.outlineWidth + 3.dp,
                        outline = Color.gray(baseBrightness).highlight(1f)
                    )
                }.withBack
            },
            DownSemantic.override {
                it.copy(id = "dwn", background = it.background.closestColor().toHSP().let {
                    it.copy(brightness = it.brightness + brightnessStep * 3)
                }.toRGB(), outline = it.outline.closestColor().toHSP().let {
                    it.copy(brightness = it.brightness + brightnessStep * 3)
                }.toRGB(), outlineWidth = it.outlineWidth * 2).withBack
            },

            FieldSemantic.override {
                it.copy(
                    id = "fld",
                    outlineWidth = 1.px,
                    background = it.background.closestColor(),
                    cascading = false,
//                gap = it.gap / 2,
                    cornerRadii = when (val base = it.cornerRadii) {
                        is CornerRadii.RatioOfSize -> base
                        is CornerRadii.RatioOfSpacing -> CornerRadii.Fixed(it.gap * base.value)
                        is CornerRadii.PerCorner -> base
                        is CornerRadii.AdaptiveToSpacing -> CornerRadii.Fixed(base.value)
                        is CornerRadii.Fixed -> base
                    }
                ).withBack
            },

            BarSemantic.override { it.withBack },
            NavSemantic.override { it.withBack },
            OuterSemantic.override {
                it.withBack(
                    cascading = false,
                    gap = 1.px,
                    padding = Edges.ZERO,
                    background = Color.gray(0.3f)
                )
            },
            MainContentSemantic.override { it.withBack },

            DialogSemantic.override {
                it.copy(id = "dlg", outlineWidth = 1.dp, gap = 2.rem, cascading = false).withBack
            },

            PopoverSemantic.override {
                it.withBack(outlineWidth = 1.dp, gap = 2.rem, cascading = false)
            },

            )
    )
}