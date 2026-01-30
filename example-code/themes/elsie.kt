package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.AdaptiveToSpacing
import com.lightningkite.kiteui.models.CornerRadii.Fixed
import com.lightningtime.Resources


fun Theme.Companion.elsie(): Theme {
    val baseColor = Color.fromHex(0x446A6A)
    val baseColorLight = Color.fromHex(0x7BA7A7)
    val back = Color.fromHex(0x0e1114)
    val accentColor = Color.fromHex(0x619292)

    val id: String = "elsie"
    val title: FontAndStyle = FontAndStyle(Resources.livvic, weight = 600, allCaps = true)
    val body: FontAndStyle = FontAndStyle(Resources.livvic)
    return Theme(
        id = id,
        font = body,
        elevation = 0.dp,
        cornerRadii = AdaptiveToSpacing(0.5.rem),
        gap = 0.75.rem,
        outlineWidth = 0.px,
        foreground = baseColorLight,
        background = back,
        outline = baseColorLight,
        semanticOverrides = SemanticOverrides(
            HeaderSemantic.override {
                it.withoutBack(font = title)
            },
            ImportantSemantic.override {
                when (it.background) {
                    baseColor -> it.withBack(background = accentColor, foreground = Color.white)
                    accentColor -> it.withBack(background = back, foreground = baseColorLight)
                    else -> it.withBack(background = baseColor, foreground = Color.white)
                }
            },
            CardSemantic.override {
                when (it.background) {
                    baseColor -> it.withBack(background = accentColor, foreground = Color.white)
                    accentColor -> it.withBack(background = back, foreground = baseColorLight)
                    else -> it.withBack(background = baseColor, foreground = Color.white)
                }
            },
            ListSemantic.override {
                val d = SemanticOverrides(
                    CardSemantic.override { it.withBack }
                )
                when (it.background) {
                    baseColor -> it.withBack(
                        background = accentColor,
                        foreground = Color.white,
                        cornerRadii = Fixed(0.5.rem),
                        semanticOverrides = d
                    )

                    accentColor -> it.withBack(
                        background = back,
                        foreground = baseColorLight,
                        cornerRadii = Fixed(0.5.rem),
                        semanticOverrides = d
                    )

                    else -> it.withBack(
                        background = baseColor,
                        foreground = Color.white,
                        cornerRadii = Fixed(0.5.rem),
                        semanticOverrides = d
                    )
                }
            },
            FieldSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp
                )
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
                it.withBack(outlineWidth = 1.dp, gap = 2.rem, cascading = false)
            },
            PopoverSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    gap = 2.rem,
                    cascading = false
                )
            },
        )
    )
}