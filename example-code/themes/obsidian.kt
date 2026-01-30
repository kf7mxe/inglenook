package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.Fixed

private fun g(l: Float = 0.05f) = LinearGradient(
    listOf(
        GradientStop(0f, Color.gray(l)),
        GradientStop(0.25f, Color.gray(l + 0.03f)),
        GradientStop(0.5f, Color.gray(l + 0.02f)),
        GradientStop(0.57f, Color.gray(l + 0.06f)),
        GradientStop(0.6f, Color.gray(l + 0.02f)),
        GradientStop(0.7f, Color.gray(l + 0.08f)),
        GradientStop(0.85f, Color.gray(l + 0.02f)),
        GradientStop(1f, Color.gray(l)),
    ),
    angle = 0.05.turns,
    screenStatic = false
)

fun Theme.Companion.obsidian(primary: Color): Theme = Theme(
    id = "obsidian-${primary.toInt()}",
    font = FontAndStyle(),
    foreground = Color.gray(0.90f),
    background = g(0.10f),
    outline = Color.gray(0.2f),
    outlineWidth = 1.dp,
    elevation = 0.px,
    gap = 0.75.rem,
    padding = Edges(0.75.rem),
    cornerRadii = Fixed(0.3.rem),
    semanticOverrides = SemanticOverrides(
        CardSemantic.override { it.withBack(outlineWidth = 1.dp, background = g(0f), foreground = Color.gray(0.90f)) },
        OuterSemantic.override { it.withBack(cascading = false, gap = 0.px, padding = Edges.ZERO) },
        BarSemantic.override { it[CardSemantic] },
        NavSemantic.override { it[CardSemantic] },
        MainContentSemantic.override { it.withBack },
        ImportantSemantic.override {
            val primaryFixed = primary.darken(0.3f)
            it.withBack(
                background = primaryFixed,
                foreground = if (primaryFixed.perceivedBrightness < 0.6f) Color.white else Color.black,
            )
        },
    )
)