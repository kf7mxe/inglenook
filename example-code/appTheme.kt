package com.lightningtime.theming

import com.lightningkite.kiteui.PlatformStorage
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.interceptWrite
import com.lightningkite.reactive.extensions.value
import com.lightningkite.services.database.modification
import com.lightningkite.services.database.notNull
import com.lightningtime.*
import com.lightningtime.sdk.rawSession
import com.lightningtime.theming.themes.*
import com.lightningtime.utils.decode
import com.lightningtime.utils.getOrPut
import kotlinx.serialization.Serializable

sealed interface Wallpaper {
    val imageSource: Reactive<ImageSource?>

    data class Resource(val image: ImageResource) : Wallpaper {
        override val imageSource = Constant(image)
    }

    data object UserPreference : Wallpaper {
        val local = Signal<ImageLocal?>(null)

        override val imageSource: Reactive<ImageSource?> = remember {
            local() ?: rawSession()
                ?.currentMembership?.invoke()
                ?.theme
                ?.wallpaper
                ?.location
                ?.let(::ImageRemote)
        }
    }
}

@Serializable
enum class ThemePreference(
    val display: String,
    val theme: ReactiveContext.(ThemeSettings) -> Theme,
    val defaultOnly: Boolean = true,
    val wallpaper: Wallpaper? = null,
    val barWallpaper: Wallpaper? = null,
) {
    Default(
        "Default",
        theme = { Theme.default(it.primaryColor?.toColor()) },
        defaultOnly = false
    ),
    REPS(
        "REPS",
        theme = { Theme.reps() },
        barWallpaper = Wallpaper.Resource(Flavor.current.imagesDefaultBar())
    ),
    LightningKite(
        "Lightning Kite",
        theme = { Theme.lk() }
    ),
    Custom(
        "Custom",
        theme = { Theme.custom(it) },
        wallpaper = Wallpaper.UserPreference
    ),
    Elsie(
        "Elsie",
        theme = { Theme.elsie() }
    ),
    ElsieCalm(
        "Elsie Calm",
        theme = { Theme.elsieCalm() }
    ),
    DefaultGradient(
        "Gradient",
        theme = { Theme.defaultGradient((it.primaryColor?.toColor() ?: Color.blue).darken(0.7f)) },
        defaultOnly = false
    ),
    DefaultGradientLight(
        "GradientLight",
        theme = { Theme.defaultGradient((it.primaryColor?.toColor() ?: Color.blue).lighten(0.8f)) },
        defaultOnly = false
    ),
    Material(
        "Material",
        theme = {
            Theme.material(
                it.primaryColor?.toColor() ?: HSPColor(
                    hue = 0.7.turns,
                    saturation = 0.8f,
                    brightness = 0.8f
                ).toRGB()
            )
        },
        defaultOnly = false
    ),
    Hackerman(
        "Hackerman",
        theme = { Theme.hackerman(it.primaryColor?.toColor() ?: Color.green) },
        defaultOnly = false
    ),
    Clouds(
        "Clouds",
        theme = {
            Theme.clouds(
                it.primaryColor?.toColor() ?: HSPColor(
                    hue = 0.7.turns,
                    saturation = 0.8f,
                    brightness = 0.8f
                ).toRGB()
            )
        },
        defaultOnly = false
    ),
    Clean(
        "Clean",
        theme = {
            Theme.clean(
                it.primaryColor?.toColor() ?: HSPColor(
                    hue = 0.7.turns,
                    saturation = 0.8f,
                    brightness = 0.8f
                ).toRGB()
            )
        },
        defaultOnly = false
    ),
    Obsidian(
        "Obsidian",
        theme = {
            Theme.obsidian(
                it.primaryColor?.toColor() ?: HSPColor(
                    hue = 0.7.turns,
                    saturation = 0.5f,
                    brightness = 0.8f
                ).toRGB(),
            )
        },
        defaultOnly = false
    ),
}

val themeSettings = mutableRemember {
    rawSession()?.currentMembership()?.theme ?: ThemeSettings(Flavor.current.defaultTheme.name)
}.interceptWrite { new ->
    value = new
    rawSession()?.currentMembership?.modify(
        modification {
            it.theme assign new
        }
    )
}

val themePreference = mutableRemember {
    val name = themeSettings().name
    ThemePreference.entries.find { it.name == name } ?: Flavor.current.defaultTheme
}.interceptWrite { new ->
    value = new
    val session = rawSession()
    val currentMember = session?.currentMembership() ?: return@interceptWrite
    if (currentMember.theme == null) {
        session.currentMembership.modify(
            modification {
                it.theme assign ThemeSettings(name = new.name)
            }
        )
    } else {
        session.currentMembership.modify(
            modification {
                it.theme.notNull.name assign new.name
            }
        )
    }
}

val appTheme = remember {
    val preference = themePreference()
    wallpaper.value = preference.wallpaper?.imageSource()
    barWallpaper.value = preference.barWallpaper?.imageSource()
    preference.run { theme(themeSettings()) }
}

data object HiddenButtonSemantic : Semantic("hdnBtn") {
    override fun default(theme: Theme): ThemeAndBack = theme.withoutBack(
        semanticOverrides = SemanticOverrides(
            HoverSemantic.override { it.withoutBack },
            DownSemantic.override { it.withoutBack },
            ClickableSemantic.override { it.withoutBack },
        )
    )
}

data object TestServerSemantic : Semantic("tstsvr") {
    override fun default(theme: Theme): ThemeAndBack = theme.withBack(
        outlineWidth = 3.dp,
        background = Color.fromHexString("6d0101"),
        outline = Color.fromHexString("fd0101"),
        foreground = Color.white,
    )
}