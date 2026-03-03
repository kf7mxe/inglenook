package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.FullScreen
import com.kf7mxe.inglenook.Resources
import com.kf7mxe.inglenook.components.librarySelector
import com.kf7mxe.inglenook.jellyfin.selectedLibraryIds
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.reactive.core.Constant

class LibrarySelectionPage : Page, FullScreen {
    override val title get() = Constant("Select Libraries")

    override fun ViewWriter.render() {
        centered.col {
            padding = 2.rem
            gap = 1.rem

            sizedBox(SizeConstraints(maxWidth = 28.rem)).col {
                gap = 1.5.rem

                centered.col {
                    gap = 0.5.rem
                    centered.h1 { content = "Inglenook" }
                    centered.subtext { content = "Choose which libraries to show" }
                    sizeConstraints(width = 7.rem).image {
                        source = Resources.jellyfinIcon
                    }
                }

                librarySelector()

                subtext {
                    ::content {
                        if (selectedLibraryIds().isEmpty()) "All libraries will be shown"
                        else "${selectedLibraryIds().size} libraries selected"
                    }
                }

                button {
                    centered.text("Continue")
                    onClick {
                        mainPageNavigator.navigate(HomePage())
                    }
                    themeChoice += ImportantSemantic
                }
            }
        }
    }
}
