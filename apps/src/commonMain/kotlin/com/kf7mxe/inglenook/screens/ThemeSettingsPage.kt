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
import com.kf7mxe.inglenook.cache.clearImageCaches
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.storage.deleteImageFromStorage
import com.kf7mxe.inglenook.storage.readImageFromStorage
import com.kf7mxe.inglenook.storage.saveFile
import com.kf7mxe.inglenook.storage.saveImageToStorage
import com.kf7mxe.inglenook.theming.createTheme
import com.kf7mxe.inglenook.util.getFileExtensionFromMimeType
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.mimeType
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.requestFile
import com.lightningkite.kiteui.views.atBottomEnd
import com.lightningkite.kiteui.views.bold
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.files.ServerFile
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        // Local state for theme customization - initialize from persisted settings
        val savedSettings = persistedThemeSettings.value
        val selectedPreset = Signal(persistedThemePreset.value)
        val customPrimaryColor = Signal<Color>(Color.fromHexString(savedSettings.primaryColor ?:"#fff"))
        val customSecondaryColor = Signal<Color>(Color.fromHexString(savedSettings.secondaryColor ?: "#fff"))
        val customAccentColor = Signal<Color>(Color.fromHexString(savedSettings.accentColor ?: "#fff"))
        val baseOpacity = Signal(savedSettings.baseOpacity)
        val opacityStep = Signal(savedSettings.opacityStep)
        val outlineOpacity = Signal(savedSettings.outlineOpacity)

        // Layout settings
        val cornerRadiusValue = Signal(savedSettings.cornerRadius)
        val paddingValue = Signal(savedSettings.padding)
        val gapValue = Signal(savedSettings.gap)
        val elevationValue = Signal(savedSettings.elevation)
        val outlineWidthValue = Signal(savedSettings.outlineWidth)

        val wallpaperPath = Signal(savedSettings.wallpaperPath)
        val wallpaperBlur = Signal(savedSettings.wallpaperBlurRadius)

        val showPlayingBookCoverAsWallpaper = Signal(savedSettings.showPlayingBookCoverAsWallpaper)
        val showPlayingBookCoverOnNowPlayingAndBookDetail = Signal(savedSettings.showPlayingBookCoverOnNowPlayingAndBookDetail)
        // Blur settings
        val blurRadius = Signal(savedSettings.blurRadius)

        // Apply theme changes
        fun applyTheme() {
            val settings = ThemeSettings(
                primaryColor = customPrimaryColor.value.closestColor().toHexString(),
                secondaryColor = customSecondaryColor.value.toHexString(),
                accentColor = customAccentColor.value.toHexString(),
                baseOpacity = baseOpacity.value,
                opacityStep = opacityStep.value,
                outlineOpacity = outlineOpacity.value,
                cornerRadius = cornerRadiusValue.value,
                padding = paddingValue.value,
                gap = gapValue.value,
                elevation = elevationValue.value,
                outlineWidth = outlineWidthValue.value,
                showPlayingBookCoverAsWallpaper = showPlayingBookCoverAsWallpaper.value,
                showPlayingBookCoverOnNowPlayingAndBookDetail = showPlayingBookCoverOnNowPlayingAndBookDetail.value,
                blurRadius = blurRadius.value,
                wallpaperPath = wallpaperPath.value,
            )
            // Persist theme settings
            persistedThemePreset.value = selectedPreset.value
            persistedThemeSettings.value = settings
            // Update reactive theme
            appTheme.value = createTheme(selectedPreset.value, settings)
        }

        scrolling.col {
            // Theme Presets Section
            col {

                h3 { content = "Theme Preset" }
                subtext { content = "Choose a base theme style" }

                fun ViewWriter.themePresetCard(
                    preset: ThemePreset,
                    selectedPreset: Signal<ThemePreset>,
                    onSelect: () -> Unit
                ) {
                    button {
                        expanding.card.row {
                            // Theme color preview
                            sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)).frame {
                                val previewTheme = createTheme(preset)
                                themeChoice += ThemeDerivation {
                                    previewTheme.copy(
                                        id = "preview-${preset.name}",
                                        cornerRadii = CornerRadii.Fixed(0.5.rem)
                                    ).withBack
                                }
                            }

                            // Theme name and description
                            expanding.col {
                                text { content = preset.displayName }
                                subtext {
                                    content = when (preset) {
                                        ThemePreset.Cozy -> "Light and cozy"
                                        ThemePreset.AutumnCabin -> "Orange warm vibes"
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

                // Theme preset grid (2 columns)
                row {
                    col {
                        for (preset in ThemePreset.entries.filterIndexed { i, _ -> i % 2 == 0 }) {
                            themePresetCard(preset, selectedPreset) {
                                selectedPreset.value = preset
                                applyTheme()
                            }
                        }
                    }

                    col {
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

            val uploadWallpaper = Action("Upload wallpaper", frequencyCap = 1.seconds) {
                val file = context.requestFile(listOf("image/png", "image/jpeg")) ?: return@Action
                file.mimeType()
                val fileExtension = getFileExtensionFromMimeType(file.mimeType())
                val fileName = "${Uuid.random()}"
                saveImageToStorage("images",fileName, ImageLocal(file),fileExtension)
                wallpaperPath.set("images/$fileName.$fileExtension")
            }

            // Background Effects Section
            col {

                h3 { content = "Background Effects" }
                subtext { content = "Apply visual effects to Now Playing, detail screens and app background" }

                card.col {
                    bold.text("Wallpaper")
                        subtext { content = "Show a wallpaper behind the app" }
                        val uploadError = Signal(false)


                        frame {

                            val wallpaper = rememberSuspending {
                                val path = wallpaperPath()?:savedSettings.wallpaperPath
                                val parent = path?.split("/")?.first()
                                val filename = path?.split("/")?.last()
                                parent?.let {parent ->
                                    filename?.let {filename ->
                                        readImageFromStorage(parent,filename)
                                    }
                                }
                            }

//                            centered.sizeConstraints(
//                                height = 12.rem,
//                                aspectRatio = Pair(16, 9)
//                            ).card.unpadded.
                            button {
                                image {
                                    scaleType = ImageScaleType.Crop
                                    rView::shown { wallpaper() != null }
                                    ::source { wallpaper() }
                                }

                                centered.icon {
                                    ::shown { wallpaper() == null && !uploadError() }
                                    source = Icon.Companion.add.copy(2.5.rem, height = 2.5.rem)
                                    description = "Upload new wallpaper"
                                }

                                centered.text {
                                    ::shown { uploadError() }
                                    content = "There was issue with uploading your file. Please try again."
                                    align = Align.Center
                                }

                                action = uploadWallpaper
                            }
                            atBottomEnd.row {
                                ::shown { wallpaper() != null }
                                card.button {
                                    icon(Icon.Companion.edit, "Change wallpaper")
                                    action = uploadWallpaper
                                }
                                card.button {
                                    icon(Icon.Companion.delete, "Delete")
                                    onClick("Delete Wallpaper") {
                                        confirmDanger(
                                            "Delete Wallpaper",
                                            "Are you sure you want to delete your wallpaper? This cannot be undone.",
                                            "Delete"
                                        ) {
                                            deleteImageFromStorage(wallpaperPath.invoke()?:"")
                                            wallpaperPath.set(null)
                                        }
                                    }
                                }
                            }
                        }

                    col {
                        row {
                            expanding.text { content = "Wallpaper Blur Intensity" }
                            text { ::content { "${wallpaperBlur().toInt()}px" } }
                        }
                        slider {
                            min =0.0f
                            max = 50.0f
                            value bind wallpaperBlur
                        }
                    }


                    row {
                        checkbox {
                            checked.bind(showPlayingBookCoverAsWallpaper)
                        }
                        col {
                            text("Use Playing Book Cover as wallpaper")
                            subtext { content = "Instead of showing wallpaper, if there is nothing playing and a wallpaper is set then it will show the wallpaper" }
                        }
                    }


                    // Blurred background toggle
                    row {
                        checkbox {
                            checked bind showPlayingBookCoverOnNowPlayingAndBookDetail
                        }
                        col {
                            text { content = "Show Book Cover Background" }
                            subtext { content = "Show cover image behind content on now playing bottom and sheet and book detail" }
                        }
                    }
                    // Blur radius slider (only shown when blur is enabled)
                    col {
                        row {
                            expanding.text { content = "Blur Intensity" }
                            text { ::content { "${blurRadius().toInt()}px" } }
                        }
                        slider {
                            min =0.0f
                            max = 50.0f
                            value bind blurRadius
                        }
                    }


                    // Apply button for blur settings
                    button {
                        row {
                            centered.icon(Icon.check, "Apply")
                            text("Apply Background Effects")
                        }
                        onClick {
                            clearImageCaches()
                            applyTheme()
                        }
                        themeChoice += ImportantSemantic
                    }
                }
            }

            separator()

            // Accent Color Section (shown for presets that allow customization)
            shownWhen { selectedPreset().allowsCustomization && selectedPreset() != ThemePreset.Custom }.col {
                h3 { content = "Accent Color" }
                subtext { content = "Customize the primary accent color" }

                // Color palette
                row {
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
                                customPrimaryColor.value = Color.fromHexString(colorHex)
                                applyTheme()
                            }
                            dynamicTheme {
                                if (customPrimaryColor() == Color.fromHexString(colorHex)) SelectedSemantic else null
                            }
                        }
                    }
                }

                // Hex input
                col {

                    row {
                        text { content = "Hex:" }
//                        expanding.textInput {
//                            hint = "#364a3b"
//                            content bind customPrimaryColor
//                        }
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
                        icon(Icon.close, "Reset")
                        text("Reset to Default")
                    }
                    onClick {
//                        customPrimaryColor.value = ""
                        applyTheme()
                    }
                }
            }

            // Custom Theme Section (only shown for Custom preset)
            shownWhen { selectedPreset() == ThemePreset.Custom }.col {

                h3 { content = "Custom Theme Settings" }
                subtext { content = "Fine-tune your theme appearance" }

                card.col {


                    // Primary Color
                    col {
                        text { content = "Primary Color (accents, buttons)" }
                        row {
                            expanding.textInput {
                                hint = "#6366f1"
                                content bind customPrimaryColor.lens(get = {it.toHexString()}, modify = {color,updated ->
                                    Color.fromHexString(updated)
                                })
                            }

                            sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                expanding.colorPicker {
                                    color.bind(customPrimaryColor)
                                }
//                                themeChoice += ThemeDerivation {
//                                    val color = customPrimaryColor.value
//                                        ?.let { runCatching { it }.getOrNull() }
//                                        ?: Color.fromHexString("#6366f1")
//                                    it.copy(
//                                        id = "primary-preview",
//                                        background = color,
//                                        cornerRadii = CornerRadii.Fixed(0.25.rem)
//                                    ).withBack
//                                }
                            }
                        }
                    }

                    // Secondary Color (background)
                    col {
                        text { content = "Background Color" }
                        row {
                            expanding.textInput {
                                hint = "#1a1a2e"
                                content bind customSecondaryColor.lens(get = {it.toHexString()}, modify = {color,updated ->
                                    Color.fromHexString(updated)
                                })
                            }
                            sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                expanding.colorPicker {
                                    color.bind(customSecondaryColor)
                                }
                            }
                        }
                    }

                    // Accent Color (outlines)
                    col {
                        text { content = "Outline/Accent Color" }
                        row {
                            expanding.textInput {
                                hint = "#3b3b4d"
                                content bind customAccentColor.lens(get = {it.toHexString()}, modify = {color,updated ->
                                    Color.fromHexString(updated)
                                })                            }
                            sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                expanding.colorPicker {
                                    color.bind(customAccentColor)
                                }
                            }
                        }
                    }

                    separator()

                    // Opacity Sliders
                    col {
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

                    separator()

                    // Layout Settings
                    h4 { content = "Layout Settings" }

                    col {
                        row {
                            expanding.text { content = "Corner Radius" }
                            text {
                                ::content {
                                    "${((cornerRadiusValue() * 100).roundToInt() / 100.0)}.rem"
                                }
                            }
                        }
                        slider {
                            min = 0.0f
                            max = 10.0f
                            value bind cornerRadiusValue
                        }

                        row {
                            expanding.text { content = "Padding" }
                            text { ::content { "${((paddingValue() * 100).roundToInt() / 100.0)}.rem" } }
                        }
                        slider {
                            min = 0.25f
                            max = 2.0f
                            value bind paddingValue
                        }
                        row {
                            expanding.text { content = "Gap" }
                            text { ::content { "${((gapValue() * 100).roundToInt() / 100.0)}rem" } }
                        }
                        slider {
                            min = 0.25f
                            max = 2.0f
                            value bind gapValue
                        }
                        row {
                            expanding.text { content = "Elevation" }
                            text { ::content { "${((elevationValue() * 100).roundToInt() / 100.0)}dp" } }
                        }
                        slider {
                            min = 0.0f
                            max = 8.0f
                            value bind elevationValue
                        }
                        row {
                            expanding.text { content = "Outline Width" }
                            text { ::content { "${((outlineWidthValue() * 100).roundToInt() / 100.0)}dp" } }
                        }
                        slider {
                            min = 0.0f
                            max = 4.0f
                            value bind outlineWidthValue
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

                    h3 { content = "Preview" }
                    subtext { content = "See how the theme looks" }

                    card.col {

                        h3 { content = "Sample Header" }
                        text { content = "This is regular text content showing how the theme displays text." }
                        subtext { content = "This is subtext or secondary content." }

                        row {
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
                            text { content = "Nested card content" }
                            subtext { content = "Cards can contain other elements" }
                        }


                        row {
                            expanding.textInput {
                                hint = "Sample input field"
                            }
                            button {
                                icon(Icon.search, "Search")
                            }
                        }
                    }
                }

                themed(ImageSemantic).sizeConstraints(width = 10.rem, height=5.rem).image{
                    source = Resources.example
                    scaleType = ImageScaleType.Crop
                }


                // Spacer at bottom
                space(2.0)
            }
        }
    }


}