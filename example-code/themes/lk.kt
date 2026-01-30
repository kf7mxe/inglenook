package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.AdaptiveToSpacing
import com.lightningkite.kiteui.models.CornerRadii.Fixed
import com.lightningtime.ButtonBarSemantic
import com.lightningtime.Resources


fun Theme.Companion.lk(): Theme {
    val background = Color.fromHex(0x08181D)
    val foreground = Color.white
    val card = Color.fromHex(0x133C4A)
    val importantBack = Color.fromHex(0x1b596e)
    val titleColor = Color.fromHex(0xF4B61B)
    val title: FontAndStyle = FontAndStyle(Resources.barlow, allCaps = true)
    val body: FontAndStyle = FontAndStyle(Resources.lato)

    return Theme(
        id = "lk",
        font = body,
        elevation = 0.dp,
        cornerRadii = AdaptiveToSpacing(0.5.rem),
        gap = 0.75.rem,
        outlineWidth = 0.px,
        iconOverride = titleColor,
        foreground = foreground,
        background = background,
        outline = foreground,
        semanticOverrides = SemanticOverrides(
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
            HeaderSemantic.override {
                it.withoutBack(font = title, foreground = titleColor)
            },
            CardSemantic.override {
                it.withBack(
                    background = if (it.background == background) card else it.background.lighten(0.05f),
                    foreground = foreground
                )
            },
            ImportantSemantic.override {
                it.withBack(
                    background = importantBack,
                    foreground = foreground
                )
            },
            ErrorSemantic.override {
                it.withoutBack(
                    foreground = it[DangerSemantic].theme.background.closestColor().highlight(0.2f)
                )
            },
            CriticalSemantic.override {
                it.withBack(
                    background = titleColor,
                    foreground = titleColor.highlight(1f)
                )
            },
            DialogSemantic.override {
                it.alter(
                    cascading = false,
                    elevation = 0.5.rem,
                    cornerRadii = AdaptiveToSpacing(1.rem),
                    padding = Edges(1.rem),
                ).withBack(
                    cascading = true,
                    background = it.background.lighten(0.05f)
                )
            },
            PopoverSemantic.override {
                it.alter(
                    cascading = false,
                    elevation = 0.5.rem,
                    cornerRadii = AdaptiveToSpacing(1.rem),
                    padding = Edges(1.rem),
                ).withBack(
                    cascading = true,
                    background = it.background.lighten(0.05f)
                )
            },
            ListSemantic.override {
                it.withoutBack(gap = 2.dp, cascading = false)
            },
            ButtonBarSemantic.override {
                it[CardSemantic].theme.alter(
                    cascading = false,
                    gap = 0.px,
                    cornerRadii = Fixed(value = 1.rem),
                    padding = Edges(1.dp),
                    foreground = it.background,
                ).withBack(
                    cascading = true,
                    semanticOverrides = SemanticOverrides(
                        ButtonSemantic.override {
                            it.withBack(
                                cornerRadii = AdaptiveToSpacing(value = 1.rem),
                                padding = Edges(horizontal = 1.rem, vertical = 0.5.rem),
                            )
                        },
                    )
                )
            }
        )
    )
}