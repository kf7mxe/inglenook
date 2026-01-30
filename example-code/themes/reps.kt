package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.AdaptiveToSpacing
import com.lightningkite.kiteui.models.CornerRadii.Fixed
import com.lightningtime.*
import com.lightningtime.components.BoxBreakerStyling
import com.lightningtime.components.Discounted
import com.lightningtime.components.MostPopular
import com.lightningtime.theming.highlightTo


fun Theme.Companion.reps(): Theme {
    val background = Color.white
    val primary = Color.fromHex(0x517CA9)
    val primaryDark = Color.fromHex(0x0E377A)
    val title = FontAndStyle(Resources.inter, weight = 600)
    val body = FontAndStyle(Resources.inter)

    return Theme(
        id = "reps",
        font = body,
        elevation = 0.dp,
        cornerRadii = AdaptiveToSpacing(0.75.rem),
        gap = 1.rem,
        outlineWidth = 0.px,
        foreground = primaryDark,
        background = background,
        outline = primaryDark,
        semanticOverrides = SemanticOverrides(
            BarSemantic.override {
                it.withBack(
                    background = LinearGradient(
                        stops = listOf(
                            GradientStop(0f, primary.withAlpha(0f)),
                            GradientStop(1f, primary.withAlpha(1f)),
                        ),
                        angle = 0.25.turns,
                        screenStatic = false,
                    ),
                    gap = 0.5.rem,
                    padding = Edges(0.5.rem),
                    foreground = Color.white,
                    iconOverride = Color.white
                )
            },
            NavSemantic.override { it.withBack },
            OuterSemantic.override {
                it.withBack(
                    cascading = false,
                    gap = 0.px,
                    padding = Edges.ZERO,
                    cornerRadii = CornerRadii.Fixed(0.dp),
                )
            },
            InvalidSemantic.override {
                it.withBack(
                    cascading = false,
                    outlineWidth = 1.px,
                    outline = Color.red
                )
            },
            FieldSemantic.override {
//                val b = it.background.closestColor().darken(0.05f)
//                val f = if (b.perceivedBrightness > 0.8f) primaryDark else b.closestColor().highlight(1f)
                it.alter(
//                    background = b,
//                    foreground = f,
                    outline = Color.gray(0.7f),
                    separatorOverride = Color.gray(0.7f),
                    padding = it.padding / 2,
                    gap = it.gap / 2,
                    cornerRadii = Fixed(0.5.rem),
                ).withBack(
                    cascading = false,
                    outlineWidth = 1.px
                )
            },
            CardSemantic.override {
                val b = it.background.closestColor().darken(0.05f)
                it.alter(
//                    background = b,
//                    foreground = if (b.perceivedBrightness > 0.8f) primaryDark else b.closestColor().highlight(1f),
                    outline = Color.gray(0.7f),
                    separatorOverride = Color.gray(0.7f),
                ).withBack(
                    cascading = false,
                    outlineWidth = 1.px,
                )
            },
            BigSelectButton.override { btn ->
                btn.withBack(
                    outline = Color.gray(0.7f),
                    outlineWidth = 1.dp,
                    semanticOverrides = SemanticOverrides(
                        UnselectedSemantic.override {
                            it.withBack()
                        }
                    )
                )
            },
            MainContentSemantic.override { it.withBack },
            HeaderSemantic.override {
                it.withoutBack(font = title)
            },
            ErrorSemantic.override {
                it.withoutBack(
                    foreground = it[DangerSemantic].theme.background.closestColor().highlight(0.2f)
                )
            },
            ImportantSemantic.override {
                it.withBack(
                    outlineWidth = 0.px,
                    outline = primaryDark,
                    background = primaryDark,
                    foreground = Color.white
                )
            },
            PageBarSemantic.override { pgbr ->
                pgbr[CompactSemantic].theme.withoutBack(
                    semanticOverrides = SemanticOverrides(
                        FieldSemantic.override {
                            it.withBack(
                                outlineWidth = 1.px,
                                outline = Color.gray(0.7f),
                                separatorOverride = Color.gray(0.7f),
                                cornerRadii = Fixed(0.5.rem),
                            )
                        }
                    )
                )
            },
            CriticalSemantic.override {
                it.withBack(
                    background = primaryDark,
                    foreground = Color.white,
                    padding = it.padding * 2
                )
            },
            DialogSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    padding = Edges(1.rem),
                    cornerRadii = Fixed(1.rem),
                    cascading = false
                )
            },
            PopoverSemantic.override {
                it.withBack(
                    outlineWidth = 1.dp,
                    padding = Edges(1.rem),
                    cornerRadii = Fixed(1.rem),
                    cascading = false
                )
            },
            HoverSemantic.override { hvr ->
                val b = hvr.background.closestColor().highlightTo(0.2f, darker = hvr.foreground.closestColor())
                hvr.withBack(
                    background = b,
                    outline = b.highlight(0.05f)
                )
            },
            SelectedSemantic.override { sel ->
                val b = sel.background.closestColor().highlightTo(0.25f, darker = sel.foreground.closestColor())
                sel.withBack(
                    background = b,
                    outline = b.highlight(0.05f),
                    elevation = sel.elevation / 2f,
                )
            },
            ListSemantic.override {
                it.withoutBack(
                    cascading = false,
                    gap = it.gap / 2
                )
            },
            SubscriptionsSemantic.override { subs ->
                subs.withoutBack(
                    foreground = Color.fromHex(0x727272),
                    semanticOverrides = SemanticOverrides(
                        HeaderSemantic.override {
                            it.withoutBack(
                                foreground = if (it.background.closestColor().perceivedBrightness > 0.85f) primaryDark else Color.white,
                                font = title
                            )
                        },
                        MostPopular.override { BoxBreakerStyling(key, Color.fromHex(0x507baa))(it) },
                        Discounted.override { discounted ->
                            BoxBreakerStyling(key, Color.fromHex(0x92d16b))(discounted).theme.withoutBack(
                                semanticOverrides = SemanticOverrides(
                                    AffirmativeSemantic.override {
                                        it.withoutBack(
                                            foreground = Color.fromHex(0x92d16b)
                                        )
                                    },
                                    MostPopular.override {
                                        BoxBreakerStyling(key, Color.fromHex(0x37a68a))(it)
                                    }
                                )
                            )
                        },
                    )
                )
            },
            TextButton.override {
                TextButton.default(it[CompactSemantic].theme)
            }
        )
    )
}