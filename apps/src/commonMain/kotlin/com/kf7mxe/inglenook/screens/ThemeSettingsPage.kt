package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.dynamicTheme
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.theming.createTheme
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember

// Preset colors for quick accent selection
val presetColors = listOf(
    "#364a3b" to "Forest",
    "#2c5f7c" to "Ocean",
    "#6366f1" to "Indigo",
    "#c67c4e" to "Amber",
    "#10b981" to "Emerald",
    "#f43f5e" to "Rose",
    "#8b5cf6" to "Violet",
    "#06b6d4" to "Cyan"
)

@Routable("/settings/theme")
class ThemeSettingsPage : Page {
    override val title get() = Constant("Theme Settings")

    override fun ViewWriter.render() {
        // Local state for theme customization
        val selectedPreset = Signal(currentThemePreset.value)
        val customPrimaryColor = Signal("")
        val customSecondaryColor = Signal("")
        val customAccentColor = Signal("")
        val baseOpacity = Signal(0.9f)
        val opacityStep = Signal(0.1f)
        val outlineOpacity = Signal(0.6f)

        // Apply theme changes
        fun applyTheme() {
            val settings = ThemeSettings(
                primaryColor = customPrimaryColor.value.takeIf { it.isNotBlank() },
                secondaryColor = customSecondaryColor.value.takeIf { it.isNotBlank() },
                accentColor = customAccentColor.value.takeIf { it.isNotBlank() },
                baseOpacity = baseOpacity.value,
                opacityStep = opacityStep.value,
                outlineOpacity = outlineOpacity.value
            )
            currentThemePreset.value = selectedPreset.value
            appTheme.value = createTheme(selectedPreset.value, settings)
        }

        scrolling.col {
            padding = 1.rem
            gap = 1.5.rem

            // Theme Presets Section
            col {
                gap = 0.75.rem

                h3 { content = "Theme Preset" }
                subtext { content = "Choose a base theme style" }

                // Theme preset grid (2 columns)
                row {
                    gap = 0.75.rem

                    col {
                        gap = 0.75.rem
                        for (preset in ThemePreset.entries.filterIndexed { i, _ -> i % 2 == 0 }) {
                            themePresetCard(preset, selectedPreset) {
                                selectedPreset.value = preset
                                applyTheme()
                            }
                        }
                    }

                    col {
                        gap = 0.75.rem
                        for (preset in ThemePreset.entries.filterIndexed { i, _ -> i % 2 == 1 }) {
                            themePresetCard(preset, selectedPreset) {
                                selectedPreset.value = preset
                                applyTheme()
                            }
                        }
                    }
                }
            }

            separator()

            // Accent Color Section (shown for presets that allow customization)
            shownWhen { selectedPreset().allowsCustomization && selectedPreset() != ThemePreset.Custom }.col {
                gap = 0.75.rem

                h3 { content = "Accent Color" }
                subtext { content = "Customize the primary accent color" }

                // Color palette
                row {
                    gap = 0.5.rem
                    for ((colorHex, colorName) in presetColors) {
                        button {
                            sizedBox(SizeConstraints(width = 2.5.rem, height = 2.5.rem)).frame {
                                themeChoice += ThemeDerivation {
                                    it.copy(
                                        id = "color-preview-$colorHex",
                                        background = Color.fromHexString(colorHex),
                                        cornerRadii = CornerRadii.Fixed(0.5.rem)
                                    ).withBack
                                }
                            }
                            onClick {
                                customPrimaryColor.value = colorHex
                                applyTheme()
                            }
                            dynamicTheme {
                                if (customPrimaryColor() == colorHex) SelectedSemantic else null
                            }
                        }
                    }
                }

                // Hex input
                col {
                    gap = 0.5.rem
                    padding = 0.5.rem

                    row {
                        gap = 0.5.rem
                        text { content = "Hex:" }
                        expanding.textInput {
                            hint = "#364a3b"
                            content bind customPrimaryColor
                        }
                        button {
                            text("Apply")
                            onClick { applyTheme() }
                            themeChoice += ImportantSemantic
                        }
                    }
                }

                // Reset button
                button {
                    row {
                        gap = 0.25.rem
                        icon(Icon.close, "Reset")
                        text("Reset to Default")
                    }
                    onClick {
                        customPrimaryColor.value = ""
                        applyTheme()
                    }
                }
            }

            // Custom Theme Section (only shown for Custom preset)
            shownWhen { selectedPreset() == ThemePreset.Custom }.col {
                gap = 1.rem

                h3 { content = "Custom Theme Settings" }
                subtext { content = "Fine-tune your theme appearance" }

                card.col {
                    gap = 1.rem
                    padding = 1.rem

                    // Primary Color
                    col {
                        gap = 0.25.rem
                        text { content = "Primary Color (accents, buttons)" }
                        row {
                            gap = 0.5.rem
                            expanding.textInput {
                                hint = "#6366f1"
                                content bind customPrimaryColor
                            }
                            sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                themeChoice += ThemeDerivation {
                                    val color = customPrimaryColor.value.takeIf { it.isNotBlank() }
                                        ?.let { runCatching { Color.fromHexString(it) }.getOrNull() }
                                        ?: Color.fromHexString("#6366f1")
                                    it.copy(id = "primary-preview", background = color, cornerRadii = CornerRadii.Fixed(0.25.rem)).withBack
                                }
                            }
                        }
                    }

                    // Secondary Color (background)
                    col {
                        gap = 0.25.rem
                        text { content = "Background Color" }
                        row {
                            gap = 0.5.rem
                            expanding.textInput {
                                hint = "#1a1a2e"
                                content bind customSecondaryColor
                            }
                            sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                themeChoice += ThemeDerivation {
                                    val color = customSecondaryColor.value.takeIf { it.isNotBlank() }
                                        ?.let { runCatching { Color.fromHexString(it) }.getOrNull() }
                                        ?: Color.fromHexString("#1a1a2e")
                                    it.copy(id = "secondary-preview", background = color, cornerRadii = CornerRadii.Fixed(0.25.rem)).withBack
                                }
                            }
                        }
                    }

                    // Accent Color (outlines)
                    col {
                        gap = 0.25.rem
                        text { content = "Outline/Accent Color" }
                        row {
                            gap = 0.5.rem
                            expanding.textInput {
                                hint = "#3b3b4d"
                                content bind customAccentColor
                            }
                            sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                themeChoice += ThemeDerivation {
                                    val color = customAccentColor.value.takeIf { it.isNotBlank() }
                                        ?.let { runCatching { Color.fromHexString(it) }.getOrNull() }
                                        ?: Color.fromHexString("#3b3b4d")
                                    it.copy(id = "accent-preview", background = color, cornerRadii = CornerRadii.Fixed(0.25.rem)).withBack
                                }
                            }
                        }
                    }

                    separator()

                    // Opacity Sliders
                    col {
                        gap = 0.25.rem
                        row {
                            expanding.text { content = "Base Opacity" }
                            text { ::content { "${(baseOpacity() * 100).toInt()}%" } }
                        }
                        slider {
                            min = 0.3f
                            max = 1.0f
                            value bind baseOpacity
                        }
                    }

                    col {
                        gap = 0.25.rem
                        row {
                            expanding.text { content = "Layer Opacity Step" }
                            text { ::content { "${(opacityStep() * 100).toInt()}%" } }
                        }
                        slider {
                            min = 0.0f
                            max = 0.3f
                            value bind opacityStep
                        }
                    }

                    col {
                        gap = 0.25.rem
                        row {
                            expanding.text { content = "Outline Opacity" }
                            text { ::content { "${(outlineOpacity() * 100).toInt()}%" } }
                        }
                        slider {
                            min = 0.0f
                            max = 1.0f
                            value bind outlineOpacity
                        }
                    }

                    // Apply button
                    button {
                        row {
                            gap = 0.5.rem
                            centered.icon(Icon.check, "Apply")
                            text("Apply Custom Theme")
                        }
                        onClick { applyTheme() }
                        themeChoice += ImportantSemantic
                    }
                }
            }

            separator()

            // Theme Preview Section
            col {
                gap = 0.75.rem

                h3 { content = "Preview" }
                subtext { content = "See how the theme looks" }

                card.col {
                    gap = 0.75.rem
                    padding = 1.rem

                    h3 { content = "Sample Header" }
                    text { content = "This is regular text content showing how the theme displays text." }
                    subtext { content = "This is subtext or secondary content." }

                    row {
                        gap = 0.5.rem
                        button {
                            text("Primary")
                            themeChoice += ImportantSemantic
                        }
                        button {
                            text("Secondary")
                        }
                        button {
                            text("Selected")
                            themeChoice += SelectedSemantic
                        }
                    }

                    card.col {
                        gap = 0.25.rem
                        padding = 0.75.rem
                        text { content = "Nested card content" }
                        subtext { content = "Cards can contain other elements" }
                    }

                    row {
                        gap = 0.5.rem
                        expanding.textInput {
                            hint = "Sample input field"
                        }
                        button {
                            icon(Icon.search, "Search")
                        }
                    }
                }
            }

            // Spacer at bottom
            space(2.0)
        }
    }
}

private fun ViewWriter.themePresetCard(
    preset: ThemePreset,
    selectedPreset: Signal<ThemePreset>,
    onSelect: () -> Unit
) {
    button {
        expanding.card.row {
            gap = 0.75.rem
            padding = 0.75.rem

            // Theme color preview
            sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)).frame {
                val previewTheme = createTheme(preset)
                themeChoice += ThemeDerivation {
                    previewTheme.copy(id = "preview-${preset.name}", cornerRadii = CornerRadii.Fixed(0.5.rem)).withBack
                }
            }

            // Theme name and description
            expanding.col {
                gap = 0.25.rem
                text { content = preset.displayName }
                subtext {
                    content = when (preset) {
                        ThemePreset.Cozy -> "Warm forest tones"
                        ThemePreset.Ocean -> "Cool blue depths"
                        ThemePreset.Midnight -> "Dark and minimal"
                        ThemePreset.Sunrise -> "Warm light tones"
                        ThemePreset.Material -> "Clean and modern"
                        ThemePreset.Hackerman -> "Terminal vibes"
                        ThemePreset.Clouds -> "Soft and rounded"
                        ThemePreset.Obsidian -> "Dark with accent"
                        ThemePreset.Custom -> "Your own style"
                    }
                }
            }

            // Selection indicator
            shownWhen { selectedPreset() == preset }.centered.icon(Icon.check, "Selected")
        }

        onClick { onSelect() }

        dynamicTheme {
            if (selectedPreset() == preset) SelectedSemantic else null
        }
    }
}
