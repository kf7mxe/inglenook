package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.AdaptiveToSpacing
import com.lightningkite.kiteui.models.CornerRadii.Fixed


fun Theme.Companion.material(primary: Color): Theme = Theme(
    id = "material-${primary.toInt()}",
    font = FontAndStyle(),
    foreground = Color.gray(0.2f),
    background = Color.gray(0.95f),
    outline = Color.gray(0.75f),
    separatorOverride = Color.gray(0.75f),
    outlineWidth = 0.px,
    elevation = 0.px,
    gap = 0.75.rem,
    padding = Edges(0.75.rem),
    cornerRadii = AdaptiveToSpacing(0.75.rem),
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
        NavSemantic.override { it.withBack(cornerRadii = Fixed(0.px)) },
        MainContentSemantic.override { it.withBack },
        OuterSemantic.override {
            it.withBack(
                cascading = false,
                gap = 1.dp,
                padding = Edges.ZERO,
                background = it.separator
            )
        },
        ImportantSemantic.override {
            it.withBack(
                background = primary,
                foreground = if (primary.perceivedBrightness > 0.4f) Color.black else Color.white,
            )
        },
    )
)