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
import com.kf7mxe.inglenook.storage.saveImageToStorage
import com.kf7mxe.inglenook.theming.createTheme
import com.kf7mxe.inglenook.util.getFileExtensionFromMimeType
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.mimeType
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.requestFile
import com.lightningkite.kiteui.views.atBottomEnd
import com.lightningkite.kiteui.views.bold
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kf7mxe.inglenook.util.assignThemeColors
import com.kf7mxe.inglenook.util.extractDominantColors
import com.kf7mxe.inglenook.util.loadResizedImagePixels
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.reactiveSuspending
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Per-theme accent color palettes
fun accentColorsForPreset(preset: ThemePreset): List<Pair<String, String>> = when (preset) {
    ThemePreset.Cozy -> listOf(
        "7B8266" to "forest",
        "#A45D4B" to "Terracotta",
        "#C58273" to "Clay",
        "#4A5D6E" to "Slate",
        "#2C3E50" to "Navy",
        "#C48B30" to "Ochre",
        "#D4AC63" to "Gold",
        "#4A3C31" to "Espresso",
        "#43413E" to "Charcoal"
    )
    ThemePreset.AutumnCabin -> listOf(
        "#D48441" to "Pumpkin",
        "#B5651D" to "Cinnamon",
        "#8B4513" to "Saddle",
        "#CD853F" to "Peru",
        "#A0522D" to "Sienna",
        "#DAA520" to "Goldenrod",
        "#6B4226" to "Chestnut",
        "#CC7722" to "Ochre"
    )
    ThemePreset.Midnight -> listOf(
        "#6366f1" to "Indigo",
        "#8b5cf6" to "Violet",
        "#06b6d4" to "Cyan",
        "#10b981" to "Emerald",
        "#f43f5e" to "Rose",
        "#f59e0b" to "Amber",
        "#3b82f6" to "Blue",
        "#ec4899" to "Pink"
    )
    ThemePreset.Sunrise -> listOf(
        "#c67c4e" to "Terracotta",
        "#D4915E" to "Peach",
        "#E8A87C" to "Apricot",
        "#B5651D" to "Cinnamon",
        "#C1440E" to "Burnt Orange",
        "#DAA520" to "Goldenrod",
        "#8B6914" to "Dark Gold",
        "#A0522D" to "Sienna"
    )
    ThemePreset.NeumorphismLight -> listOf(
        "#6200EE" to "Purple",
        "#03DAC6" to "Teal",
        "#018786" to "Dark Teal",
        "#BB86FC" to "Lavender",
        "#3700B3" to "Deep Purple",
        "#CF6679" to "Pink",
        "#1976D2" to "Blue",
        "#388E3C" to "Green"
    )
    ThemePreset.NeumorphismDark -> listOf(
        "#6200EE" to "Purple",
        "#03DAC6" to "Teal",
        "#018786" to "Dark Teal",
        "#BB86FC" to "Lavender",
        "#3700B3" to "Deep Purple",
        "#CF6679" to "Pink",
        "#1976D2" to "Blue",
        "#388E3C" to "Green"
    )
    ThemePreset.Hackerman -> listOf(
        "#00FF00" to "Matrix",
        "#00FFFF" to "Cyan",
        "#FF00FF" to "Magenta",
        "#FFFF00" to "Yellow",
        "#FF4500" to "OrangeRed",
        "#00FF7F" to "Spring",
        "#7FFF00" to "Chartreuse",
        "#FF1493" to "DeepPink"
    )
    ThemePreset.Clouds -> listOf(
        "#7C9CBF" to "Sky",
        "#B5A8D0" to "Lavender",
        "#F0A3BC" to "Blush",
        "#A8D5BA" to "Mint",
        "#F5C896" to "Peach",
        "#89CFF0" to "Baby Blue",
        "#C3B1E1" to "Lilac",
        "#FADADD" to "Pink"
    )
    ThemePreset.Obsidian -> listOf(
        "#9B59B6" to "Amethyst",
        "#E74C3C" to "Ruby",
        "#2ECC71" to "Emerald",
        "#3498DB" to "Sapphire",
        "#F39C12" to "Topaz",
        "#1ABC9C" to "Turquoise",
        "#E91E63" to "Garnet",
        "#00BCD4" to "Aquamarine"
    )
    ThemePreset.Glassish -> listOf(
        "#6366f1" to "Indigo",
        "#8b5cf6" to "Violet",
        "#06b6d4" to "Cyan",
        "#10b981" to "Emerald",
        "#f43f5e" to "Rose",
        "#3b82f6" to "Blue",
        "#a855f7" to "Purple",
        "#14b8a6" to "Teal"
    )
    ThemePreset.Custom -> emptyList()
}

// Themes that show full background options (wallpaper, use cover as wallpaper)
private val fullBackgroundPresets = setOf(ThemePreset.Glassish, ThemePreset.Custom)

@Routable("/settings/theme")
class ThemeSettingsPage : Page {
    override val title get() = Constant("Theme Settings")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        // Local state for theme customization - initialize from persisted settings
        val savedSettings = persistedThemeSettings.value
        val selectedPreset = Signal(persistedThemePreset.value)
        val customPrimaryColor = Signal<Color>(Color.fromHexString(savedSettings.primaryColor ?: "#fff"))
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

        // Image semantic settings
        val imageCornerRadius = Signal(savedSettings.imageSemanticSettings?.cornerRadius ?: 0.5f)
        val imagePadding = Signal(savedSettings.imageSemanticSettings?.padding ?: 0f)
        val imageOutlineWidth = Signal(savedSettings.imageSemanticSettings?.outlineWidth ?: 0f)

        // Important semantic settings
        val importantBgColor = Signal<Color>(
            Color.fromHexString(savedSettings.importantSemanticSettings?.backgroundColor ?: "#6366f1")
        )
        val importantFgColor = Signal<Color>(
            Color.fromHexString(savedSettings.importantSemanticSettings?.foregroundColor ?: "#ffffff")
        )

        // Selected semantic settings
        val selectedBgColor = Signal<Color>(
            Color.fromHexString(savedSettings.selectedSemanticSettings?.backgroundColor ?: "#6366f1")
        )
        val selectedOutlineColor = Signal<Color>(
            Color.fromHexString(savedSettings.selectedSemanticSettings?.outlineColor ?: "#6366f1")
        )
        val selectedOutlineWidth = Signal(savedSettings.selectedSemanticSettings?.outlineWidth ?: 2f)

        // Card semantic settings
        val cardBgColor = Signal<Color>(
            Color.fromHexString(savedSettings.cardSemanticSettings?.backgroundColor ?: "#282838")
        )
        val cardOutlineColor = Signal<Color>(
            Color.fromHexString(savedSettings.cardSemanticSettings?.outlineColor ?: "#3b3b4d")
        )
        val cardOutlineWidth = Signal(savedSettings.cardSemanticSettings?.outlineWidth ?: 1f)

        // Semantic opacity settings
        val importantOpacity = Signal(savedSettings.importantSemanticSettings?.opacity ?: 1f)
        val selectedOpacity = Signal(savedSettings.selectedSemanticSettings?.opacity ?: 1f)
        val cardOpacity = Signal(savedSettings.cardSemanticSettings?.opacity ?: 1f)

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
                wallpaperBlurRadius = wallpaperBlur.value,
                imageSemanticSettings = ImageSemanticSettings(
                    cornerRadius = imageCornerRadius.value,
                    padding = imagePadding.value,
                    outlineWidth = imageOutlineWidth.value
                ),
                importantSemanticSettings = ImportantSemanticSettings(
                    backgroundColor = importantBgColor.value.toHexString(),
                    foregroundColor = importantFgColor.value.toHexString(),
                    opacity = importantOpacity.value
                ),
                selectedSemanticSettings = SelectedSemanticSettings(
                    backgroundColor = selectedBgColor.value.toHexString(),
                    outlineColor = selectedOutlineColor.value.toHexString(),
                    outlineWidth = selectedOutlineWidth.value,
                    opacity = selectedOpacity.value
                ),
                cardSemanticSettings = CardSemanticSettings(
                    backgroundColor = cardBgColor.value.toHexString(),
                    outlineColor = cardOutlineColor.value.toHexString(),
                    outlineWidth = cardOutlineWidth.value,
                    opacity = cardOpacity.value
                ),
            )
            // Persist theme settings
            persistedThemePreset.value = selectedPreset.value
            persistedThemeSettings.value = settings
            // Update reactive theme
            val newTheme = createTheme(selectedPreset.value, settings)
            appTheme.value = newTheme
        }

        // Debounced apply for reactive custom/glassish theme updates
        var applyJob: Job? = null
        fun debouncedApplyTheme() {
            if (selectedPreset.value != ThemePreset.Custom && selectedPreset.value != ThemePreset.Glassish) return
            applyJob?.cancel()
            applyJob = AppScope.launch {
                delay(100)
                applyTheme()
            }
        }

        // Make custom/glassish theme reactive
        val reactiveSignals = listOf(
            customPrimaryColor, customSecondaryColor, customAccentColor,
            importantBgColor, importantFgColor,
            selectedBgColor, selectedOutlineColor,
            cardBgColor, cardOutlineColor,
            showPlayingBookCoverOnNowPlayingAndBookDetail
        )
        val reactiveFloatSignals = listOf(
            baseOpacity, opacityStep, outlineOpacity,
            cornerRadiusValue, paddingValue, gapValue, elevationValue, outlineWidthValue,
            imageCornerRadius, imagePadding, imageOutlineWidth,
            selectedOutlineWidth, cardOutlineWidth,
            importantOpacity, selectedOpacity, cardOpacity,

        )
        for (signal in reactiveSignals) {
            signal.addListener { debouncedApplyTheme() }
        }
        for (signal in reactiveFloatSignals) {
            signal.addListener { debouncedApplyTheme() }
        }

        val uploadWallpaper = Action("Upload wallpaper", frequencyCap = 1.seconds) {
            val file = context.requestFile(listOf("image/png", "image/jpeg")) ?: return@Action
            file.mimeType()
            val fileExtension = getFileExtensionFromMimeType(file.mimeType())
            val fileName = "${Uuid.random()}"
            saveImageToStorage("images", fileName, ImageLocal(file), fileExtension)
            wallpaperPath.set("images/$fileName.$fileExtension")

            // Extract colors from the wallpaper and apply to theme
            try {
                val imageData = loadResizedImagePixels(file, 128, 128)
                val colors = extractDominantColors(imageData.pixels, imageData.width, imageData.height, 5)
                val (bgHex, primaryHex, outlineHex) = assignThemeColors(colors)
                println("Wallpaper colors extracted - bg: $bgHex, primary: $primaryHex, outline: $outlineHex")
                println("Extracted palette: ${colors.map { it.toHexString() }}")
                val primaryCol = Color.fromHexString(primaryHex)
                val outlineCol = Color.fromHexString(outlineHex)
                customSecondaryColor.value = Color.fromHexString(bgHex)
                customPrimaryColor.value = primaryCol
                customAccentColor.value = outlineCol
                // Update semantic colors so they match the extracted palette
                importantBgColor.value = primaryCol
                selectedBgColor.value = primaryCol
                selectedOutlineColor.value = primaryCol
                applyTheme()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun ViewWriter.themePresetCard(
            preset: ThemePreset,
            onSelect: () -> Unit
        ) {
            button {
                expanding.row {
                    sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)).frame {
                        val previewTheme = createTheme(preset)
                        themeChoice += ThemeDerivation {
                            previewTheme.copy(
                                id = "preview-${preset.name}",
                                cornerRadii = CornerRadii.Fixed(0.5.rem)
                            ).withBack
                        }
                    }
                    expanding.col {
                        text { content = preset.displayName }
                        subtext {
                            content = when (preset) {
                                ThemePreset.Cozy -> "Light and cozy"
                                ThemePreset.AutumnCabin -> "Orange warm vibes"
                                ThemePreset.Midnight -> "Dark and minimal"
                                ThemePreset.Sunrise -> "Warm light tones"
                                ThemePreset.NeumorphismLight -> "Neumorphism Light"
                                ThemePreset.NeumorphismDark -> "Neumorphism Dark"
                                ThemePreset.Hackerman -> "Terminal vibes"
                                ThemePreset.Clouds -> "Soft and rounded"
                                ThemePreset.Obsidian -> "Dark with accent"
                                ThemePreset.Glassish -> "Glass transparency"
                                ThemePreset.Custom -> "Your own style"
                            }
                        }
                    }
                    shownWhen { selectedPreset() == preset }.centered.icon(Icon.check, "Selected")
                }
                onClick { onSelect() }
                dynamicTheme {
                    if (selectedPreset() == preset) SelectedSemantic else null
                }
            }
        }

        fun ViewWriter.themePresetsSection() {
            col {
                h3 { content = "Theme Preset" }
                subtext { content = "Choose a base theme style" }
                row {
                    col {
                        for (preset in ThemePreset.entries.filterIndexed { i, _ -> i % 2 == 0 }) {
                            themePresetCard(preset) {
                                selectedPreset.value = preset
                                if(preset != ThemePreset.Glassish && preset !=ThemePreset.Custom) wallpaperPath.value  =null
                                applyTheme()
                            }
                        }
                    }
                    col {
                        for (preset in ThemePreset.entries.filterIndexed { i, _ -> i % 2 == 1 }) {
                            themePresetCard(preset) {
                                selectedPreset.value = preset
                                applyTheme()
                            }
                        }
                    }
                }
            }
        }

        fun ViewWriter.backgroundEffectsSection() {
            col {
                h3 { content = "Background Effects" }
                subtext { content = "Apply visual effects to Now Playing, detail screens and app background" }

                card.col {
                    // Wallpaper section - only for Glassish and Custom
                    shownWhen { selectedPreset() in fullBackgroundPresets }.col {
                        bold.text("Wallpaper")
                        subtext { content = "Show a wallpaper behind the app" }
                        val uploadError = Signal(false)

                        frame {
                            val wallpaper = rememberSuspending {
                                val path = wallpaperPath() ?: savedSettings.wallpaperPath
                                val parent = path?.split("/")?.first()
                                val filename = path?.split("/")?.last()
                                parent?.let { parent ->
                                    filename?.let { filename ->
                                        readImageFromStorage(parent, filename)
                                    }
                                }
                            }

                            button {
                                sizeConstraints(height = 15.rem).image {
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
                                            deleteImageFromStorage(wallpaperPath.invoke() ?: "")
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
                                min = 0.0f
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
                    }

                    // Book cover background - shown for ALL presets
                    row {
                        checkbox {
                            checked bind showPlayingBookCoverOnNowPlayingAndBookDetail
                        }

                        reactive {
                            showPlayingBookCoverOnNowPlayingAndBookDetail()
                            applyTheme()
                        }

                        col {
                            text { content = "Show Book Cover Background" }
                            subtext { content = "Show cover image behind content on now playing bottom and sheet and book detail"
                            wraps = true
                            }
                        }
                    }

                    col {
                        row {
                            text { content = "Blur Intensity" }
                            text { ::content { "${blurRadius().toInt()}px" } }
                        }
                        slider {
                            min = 0.0f
                            max = 50.0f
                            value bind blurRadius
                        }
                        reactiveSuspending {
                            clearImageCaches()
                            applyTheme()
                        }

                    }
//
//                    button {
//                        row {
//                            centered.icon(Icon.check, "Apply")
//                            text("Apply Background Effects")
//                        }
//                        onClick {
//                            clearImageCaches()
//                            applyTheme()
//                        }
//                        themeChoice += ImportantSemantic
//                    }
                }
            }
        }

        fun ViewWriter.accentColorSection() {
            shownWhen { selectedPreset().allowsCustomization && selectedPreset() != ThemePreset.Custom && selectedPreset() != ThemePreset.Glassish }.col {
                h3 { content = "Accent Color" }
                subtext { content = "Customize the primary accent color" }

                // Per-preset color rows (only the matching one is visible)
                for (preset in ThemePreset.entries.filter { it != ThemePreset.Custom && it != ThemePreset.Glassish }) {
                    val colors = accentColorsForPreset(preset)
                    if (colors.isNotEmpty()) {
                        shownWhen { selectedPreset() == preset }.scrolling.row {
                            for ((colorHex, _) in colors) {
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
                                        customPrimaryColor.set(Color.fromHexString(colorHex))
                                        applyTheme()
                                    }
                                    dynamicTheme {
                                        if (customPrimaryColor() == Color.fromHexString(colorHex)) SelectedSemantic else null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun ViewWriter.themePreviewSection() {
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

                themed(ImageSemantic).sizeConstraints(width = 10.rem, height = 5.rem).image {
                    source = Resources.example
                    scaleType = ImageScaleType.Crop
                }
            }
        }

        fun ViewWriter.colorPickerRow(label: String, hint: String, signal: Signal<Color>) {
            col {
                text { content = label }
                row {
                    expanding.textInput {
                        this.hint = hint
                        content bind signal.lens(
                            get = { it.toHexString() },
                            modify = { _, updated -> Color.fromHexString(updated) }
                        )
                    }
                    sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                        expanding.colorPicker {
                            color.bind(signal)
                        }
                    }
                }
            }
        }

        fun ViewWriter.semanticSettingsSections() {
            separator()

            // Image Style
            h4 { content = "Image Style" }
            col {
                row {
                    expanding.text { content = "Corner Radius" }
                    text { ::content { "${((imageCornerRadius() * 100).roundToInt() / 100.0)}rem" } }
                }
                slider {
                    min = 0f; max = 3f
                    value bind imageCornerRadius
                }
                row {
                    expanding.text { content = "Padding" }
                    text { ::content { "${((imagePadding() * 100).roundToInt() / 100.0)}rem" } }
                }
                slider {
                    min = 0f; max = 1f
                    value bind imagePadding
                }
                row {
                    expanding.text { content = "Outline Width" }
                    text { ::content { "${((imageOutlineWidth() * 100).roundToInt() / 100.0)}dp" } }
                }
                slider {
                    min = 0f; max = 4f
                    value bind imageOutlineWidth
                }
            }

            separator()

            // Important Button Style
            h4 { content = "Important Button Style" }
            col {
                colorPickerRow("Background Color", "#6366f1", importantBgColor)
                colorPickerRow("Foreground Color", "#ffffff", importantFgColor)
                row {
                    expanding.text { content = "Opacity" }
                    text { ::content { "${(importantOpacity() * 100).toInt()}%" } }
                }
                slider {
                    min = 0.1f; max = 1.0f
                    value bind importantOpacity
                }
            }

            separator()

            // Selected Item Style
            h4 { content = "Selected Item Style" }
            col {
                colorPickerRow("Background Color", "#6366f1", selectedBgColor)
                colorPickerRow("Outline Color", "#6366f1", selectedOutlineColor)
                row {
                    expanding.text { content = "Outline Width" }
                    text { ::content { "${((selectedOutlineWidth() * 100).roundToInt() / 100.0)}dp" } }
                }
                slider {
                    min = 0f; max = 6f
                    value bind selectedOutlineWidth
                }
                row {
                    expanding.text { content = "Opacity" }
                    text { ::content { "${(selectedOpacity() * 100).toInt()}%" } }
                }
                slider {
                    min = 0.1f; max = 1.0f
                    value bind selectedOpacity
                }
            }

            separator()

            // Card Style
            h4 { content = "Card Style" }
            col {
                colorPickerRow("Background Color", "#282838", cardBgColor)
                colorPickerRow("Outline Color", "#3b3b4d", cardOutlineColor)
                row {
                    expanding.text { content = "Outline Width" }
                    text { ::content { "${((cardOutlineWidth() * 100).roundToInt() / 100.0)}dp" } }
                }
                slider {
                    min = 0f; max = 6f
                    value bind cardOutlineWidth
                }
                row {
                    expanding.text { content = "Opacity" }
                    text { ::content { "${(cardOpacity() * 100).toInt()}%" } }
                }
                slider {
                    min = 0.1f; max = 1.0f
                    value bind cardOpacity
                }
            }
        }

        fun ViewWriter.customThemeSection() {
            shownWhen { selectedPreset() == ThemePreset.Custom }.col {
                h3 {
                    ::content {
                        if (selectedPreset() == ThemePreset.Glassish) "Glassish Theme Settings"
                        else "Custom Theme Settings"
                    }
                }
                subtext { content = "Fine-tune your theme appearance" }

                card.col {
                    // Primary Color
                    colorPickerRow("Primary Color (accents, buttons)", "#6366f1", customPrimaryColor)

                    // Background Color
                    colorPickerRow("Background Color", "#1a1a2e", customSecondaryColor)

                    // Accent Color (outlines)
                    colorPickerRow("Outline/Accent Color", "#3b3b4d", customAccentColor)

                    separator()

                    // Opacity Sliders
                    col {
                        row {
                            expanding.text { content = "Base Opacity" }
                            text { ::content { "${(baseOpacity() * 100).toInt()}%" } }
                        }
                        slider {
                            min = 0.3f
                            // Glassish cannot go fully opaque
                            max = if (selectedPreset.value == ThemePreset.Glassish) 0.85f else 1.0f
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
                            text { ::content { "${((cornerRadiusValue() * 100).roundToInt() / 100.0)}rem" } }
                        }
                        slider {
                            min = 0.0f
                            max = 10.0f
                            value bind cornerRadiusValue
                        }
                        row {
                            expanding.text { content = "Padding" }
                            text { ::content { "${((paddingValue() * 100).roundToInt() / 100.0)}rem" } }
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
                    }

                    // Semantic customization sections
                    semanticSettingsSections()
                }

                separator()

                // Theme Preview
                themePreviewSection()

                // Spacer at bottom
                space(2.0)
            }
        }

        scrolling.col {
            themePresetsSection()
            separator()
            accentColorSection()
            separator()
            backgroundEffectsSection()
            customThemeSection()
        }
    }
}
