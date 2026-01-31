package com.lightningtime.views.pages.preferences

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.debounceWrite
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.serialization.lensPath
import com.lightningkite.services.files.ServerFile
import com.lightningtime.*
import com.lightningtime.sdk.session
import com.lightningtime.theming.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Routable("theme")
class ThemeSettingsPage : Page {    // TODO: This can definitely be improved
    override fun ViewWriter.render() {
        scrolling.frame {
            atTopCenter.sizeConstraints(width = 50.rem).col {
                card.renderThemeSettings()
                space(4.0)
            }
        }
    }

    data class ColorTheme(val color: Color, val outline: Boolean = false) : ThemeDerivation {
        override fun invoke(theme: Theme): ThemeAndBack = Theme(
            id = "clr${color.toInt()}" + if (outline) "o" else "n",
            background = color,
            foreground = Color.white,
            outline = theme.foreground,
            outlineWidth = if (outline) 1.dp else 0.dp,
            cornerRadii = CornerRadii.RatioOfSize(0.5f),
            elevation = 0.dp
        ).withBack
    }

    val sixColorWheel = listOf(0.turns, 0.07.turns, 0.16.turns, 0.35.turns, 0.55.turns, 0.8.turns)
    val eightColorWheel =
        listOf(0.turns, 0.07.turns, 0.16.turns, 0.35.turns, 0.5.turns, 0.64.turns, 0.71.turns, 0.87.turns)

    private fun ViewWriter.colorSelector(
        binds: MutableReactive<Color?>,
        options: List<Color> = eightColorWheel.map {
            HSPColor(hue = it, saturation = 0.8f, brightness = 0.7f, alpha = 1f).toRGB()
        } + Color.white + Color.gray(0.5f) + Color.black,
    ) = row {
        expanding.space()
        fieldTheme.compact.row {
            centered.frame {
                dynamicTheme {
                    ColorTheme(binds() ?: Color.transparent, outline = true)
                }
            }
            val hexChars = (('0'..'9') + ('a'..'f') + ('A'..'F')).toSet()
            sizeConstraints(width = 6.rem).formattedTextInput {
                hint = "Hex"

                format(
                    isRawData = { it in hexChars },
                    formatter = { clean ->
                        if (clean.isEmpty()) ""
                        else "#${clean.uppercase().take(6)}"
                    }
                )
                val base = binds.lens(
                    get = { it?.toAlphalessWeb() ?: "" },
                    set = {
                        try {
                            Color.Companion.fromHexString(it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                )
                content bind base.debounceWrite(1000.milliseconds)
            }
        }
        card.button {
            text("Default")
            onClick { binds.set(null) }
        }
        for (color in options) {
            centered.themed(ColorTheme(color)).button {
                onClick {
                    binds.set(color)
                }
            }
        }
        expanding.space()
    }

    private sealed interface ThemeOptionCard {
        fun ViewWriter.render()

        data class Option(val type: ThemePreference) : ThemeOptionCard {
            override fun ViewWriter.render() {
                unpadded.frame {
                    val selected = themePreference.lens { it == type }

                    dynamicTheme {
                        ThemeDerivation.Set(type.theme(themeSettings()))
                    }

                    image {
                        rView::shown { (type.wallpaper?.imageSource() != null) }
                        scaleType = ImageScaleType.Crop
                        ::source { type.wallpaper?.imageSource() }
                    }
                    card.button {
                        centered.text {
                            ::content {
                                if (selected()) "${type.display} (Current)"
                                else type.display
                            }
                        }
                        onClick { themePreference set type }
                    }
                }
            }
        }

        data object EmptySpace : ThemeOptionCard {
            override fun ViewWriter.render() { space() }
        }
    }

    private fun ViewWriter.renderThemeSettings() = themed(GroupedInformationSemantic).col {
        h3("Theme")

        col {
            val chunkSize = 3
            val groups = ThemePreference.entries.chunked(chunkSize)
            for (group in groups) {
                val group =
                    if (group.size == chunkSize) group.map(ThemeOptionCard::Option)
                    else group.map(ThemeOptionCard::Option) + List(chunkSize - group.size) { ThemeOptionCard.EmptySpace }

                row {
                    for (type in group) with(type) { expanding.sizeConstraints(height = 3.rem).render() }
                }
            }
        }

        shownWhen(true) { themePreference() != ThemePreference.Custom }.col {
            dynamicTheme {
                if (themePreference().defaultOnly) NotRelevantSemantic else null
            }

            h3("Accent Color")

            sizeConstraints(height = 5.rem).frame {
                centered.scrollingHorizontally.shownWhen { !themePreference().defaultOnly }.colorSelector(
                    themeSettings.lensPath { it.primaryColor }.asColor()
                )
                centered.shownWhen { themePreference().defaultOnly }.text("Not available for this theme.")
            }
        }

        shownWhen(false) { themePreference() == ThemePreference.Custom }.col {
            space()

            h3("Theme Settings")

            col {
                gap = 2.rem

                fun ViewWriter.label2(label: String, content: RowOrCol.() -> Unit) = col {
                    bold.text(label)
                    content()
                }

                label2("Wallpaper") {
                    val uploadError = Signal(false)
                    val uploadWallpaper = Action("Upload wallpaper", frequencyCap = 1.seconds) {
                        val file = context.requestFile(listOf("image/png", "image/jpeg")) ?: return@Action

                        val session = session()
                        val uploader = session.uncached.uploadEarlyEndpoint.uploadFileForRequest()
                        try {
                            fetch(
                                uploader.uploadUrl,
                                HttpMethod.PUT,
                                body = file
                            )
                            themeSettings.modify {
                                it.copy(
                                    wallpaper = ServerFile(uploader.futureCallToken)
                                )
                            }
                            uploadError.value = false
                            Wallpaper.UserPreference.local.value = ImageLocal(file)
                        } catch (e: Exception) {
                            Log.Companion.error(e)
                            uploadError.value = true
                        }
                    }

                    val wallpaper = Wallpaper.UserPreference.imageSource

                    frame {
                        centered.sizeConstraints(
                            height = 12.rem,
                            aspectRatio = Pair(16, 9)
                        ).card.unpadded.button {
                            image {
                                scaleType = ImageScaleType.Crop
                                rView::shown { wallpaper() != null }
                                ::source { wallpaper() }
                            }

                            centered.icon {
                                ::shown { wallpaper() == null && !uploadError() }
                                source = Icon.Companion.add.resize(2.5.rem)
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
                                        themeSettings.modify {
                                            it.copy(wallpaper = null)
                                        }
                                        Wallpaper.UserPreference.local.value = null
                                    }
                                }
                            }
                        }
                    }
                }

                rowCollapsingToColumn(40.rem) {
                    expanding.label2("Base Opacity") {
                        slider {
                            min = 0f
                            max = 1f
                            value bind themeSettings.lensPath { it.baseOpacity }
                        }
                    }
                    expanding.label2("Opacity Step") {
                        slider {
                            min = 0f
                            max = 1f
                            value bind themeSettings.lensPath { it.opacityStep }
                        }
                    }
                    expanding.expanding.label2("Outline Opacity") {
                        slider {
                            min = 0f
                            max = 1f
                            value bind themeSettings.lensPath { it.outlineOpacity }
                        }
                    }
                }

                label2("Base Color") {
                    scrollingHorizontally.colorSelector(
                        themeSettings.lensPath { it.secondaryColor }.asColor(),
                        options = sixColorWheel.map {
                            HSPColor(hue = it, saturation = 0.5f, brightness = 0.4f, alpha = 1f).toRGB()
                        } + listOf(Color.Companion.white, Color.Companion.black)
                    )
                }

                label2("Outline Color") {
                    scrollingHorizontally.colorSelector(
                        themeSettings.lensPath { it.accent }.asColor(),
                        options = sixColorWheel.map {
                            HSPColor(hue = it, saturation = 0.5f, brightness = 0.4f, alpha = 1f).toRGB()
                        } + listOf(Color.Companion.white, Color.Companion.black)
                    )
                }
            }
        }


    }
}