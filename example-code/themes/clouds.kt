package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.Fixed

fun Theme.Companion.clouds(primary: Color): Theme = Theme(
    id = "clouds-${primary.toInt()}",
    font = FontAndStyle(),
    foreground = Color.gray(0.2f),
    background = Color.gray(0.95f),
    outlineWidth = 0.px,
    elevation = 0.px,
    cornerRadii = Fixed(1.rem),
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
    )
)