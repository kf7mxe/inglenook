package com.lightningtime.theming.themes

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.models.CornerRadii.AdaptiveToSpacing
import com.lightningkite.kiteui.models.CornerRadii.Fixed
import com.lightningtime.ChatMeSemantic
import com.lightningtime.ChatThemSemantic
import com.lightningtime.CountSemantic
import com.lightningtime.ThemeSettings
import com.lightningtime.theming.alpha
import com.lightningtime.theming.invertIfSimilarBrightness
import com.lightningtime.theming.toColor


fun Theme.Companion.custom(settings: ThemeSettings): Theme {
    val choice = settings.primaryColor?.toColor() ?: Color.white
    val background = settings.secondaryColor?.toColor() ?: Color.black
    val foreground = if (background.perceivedBrightness <= 0.5) Color.white else Color.black
    val outline = settings.accent?.toColor()?.applyAlpha(settings.outlineOpacity) ?: Color.white

    return Theme(
        id = "cstm" + (settings.hashCode() + choice.hashCode()).toString(16),
        elevation = 0.dp,
        cornerRadii = AdaptiveToSpacing(0.5.rem),
        gap = 0.75.rem,
        outlineWidth = 0.dp,
        background = background.applyAlpha(settings.baseOpacity),
        foreground = foreground,
        outline = outline,
        semanticOverrides = SemanticOverrides(
            BarSemantic.override { it.withoutBackButPadding },
            NavSemantic.override {
                it.withBack(
                    gap = 1.rem,
                    background = it.background.closestColor().copy(alpha = 0.8f)
                )
            },
            OuterSemantic.override {
                it.withBack(
                    cascading = false,
                    gap = 1.px,
                    padding = Edges.ZERO,
                    background = Color.gray(0.3f),
                    cornerRadii = Fixed(0.dp)
                )
            },
            HeaderSemantic.override {
                it.withoutBack(
                    font = it.font.copy(bold = true),
                )
            },
            MainContentSemantic.override { it.withBack },
            CardSemantic.override { theme ->
                val newBack = when {
                    theme.background.alpha < 1.0 -> theme.background.closestColor().let {
                        it.copy(alpha = (it.alpha + settings.opacityStep).coerceAtMost(1f)).lighten(0.05f)
                    }

                    else -> theme.background.lighten(0.1f)
                }

                theme.withBack(
                    background = newBack,
                    outlineWidth = 1.dp,
                    outline = outline,
                    foreground = theme.foreground.closestColor().invertIfSimilarBrightness(newBack.closestColor())
                )
            },
            CountSemantic.override {
                val back =
                    if (it.background.closestColor().perceivedBrightness > 0.5) Color.white
                    else Color.black

                it.withBack(
                    font = it.font.copy(size = 0.5.rem),
                    padding = Edges(0.25.rem),
                    cornerRadii = Fixed(9999.px),
                    foreground = back.invert(),
                    background = back.applyAlpha((it.background.alpha + settings.opacityStep).coerceAtMost(1f)),
                )
            },
            FieldSemantic.override {
                it.withBack(
                    background = it.background.closestColor().run { copy(alpha = alpha.coerceAtLeast(0.8f)) },
                    outline = it.outline.closestColor().run { copy(alpha = alpha.coerceAtLeast(0.6f)) },
                    outlineWidth = 1.px
                )
            },
            ImportantSemantic.override {
                it.withBack(
                    background = choice.applyAlpha((it.background.alpha + settings.opacityStep).coerceAtMost(1f)),
                    foreground = it.foreground.closestColor().invertIfSimilarBrightness(choice)
                )
            },
            ListSemantic.override {
                it.withoutBack(gap = 2.dp, cascading = false)
            },
            OuterSemantic.override {
                it.withoutBack
            },
            HoverSemantic.override {
                it.withBack(
                    background = it.background.lighten(0.3f)
                )
            },
            InsetSemantic.override {
                it.withBack(
                    background = Color.transparent,
                    outlineWidth = 1.px,
                    cascading = false
                )
            },
            DialogSemantic.override {
                it.withBack(
                    background = it.background.closestColor().copy(alpha = 1f),
                    outline = outline,
                    outlineWidth = 1.dp,
                    cornerRadii = AdaptiveToSpacing(0.5.rem),
                )
            },
            PopoverSemantic.override {
                it.withBack(
                    background = it.background.closestColor().copy(alpha = 1f),
                    outline = outline,
                    outlineWidth = 1.dp,
                    cornerRadii = AdaptiveToSpacing(0.5.rem),
                )
            },
            ChatMeSemantic.override { chat ->
                chat.withBack(
                    background = choice.toHSV().copy(value = 0.3f).toRGB(),
                    semanticOverrides = SemanticOverrides(
                        CardSemantic.override {
                            it.withBack(
                                background = it.background.darken(0.3f)
                            )
                        }
                    )
                )
            },
            SelectedSemantic.override {
                it.withBack(
                    background = choice.applyAlpha(it.background.alpha),
                    outlineWidth = 0.dp
                )
            },
            ChatThemSemantic.override { chat ->
                chat.withBack(
                    background = background,
                    semanticOverrides = SemanticOverrides(
                        CardSemantic.override {
                            it.withBack(
                                background = Color.interpolate(it.background.closestColor(), choice, 0.2f)
                            )
                        }
                    )
                )
            }
        )
    )
}